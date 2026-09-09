package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.core.level.LevelState
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.LobbyState
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.LeaveReason
import ch.hippmann.godot.replication.diagnostics.NetworkStatistics
import ch.hippmann.godot.replication.rpc.Received
import ch.hippmann.godot.replication.rpc.Target
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import ch.hippmann.godot.replication.session.ReplicationManager
import ch.hippmann.godot.replication.session.SessionRuntime
import ch.hippmann.godot.replication.session.host
import ch.hippmann.godot.replication.session.join
import ch.hippmann.godot.replication.session.joinByCode
import ch.hippmann.godot.replication.session.leave
import ch.hippmann.godot.replication.transport.LanDiscovery
import ch.hippmann.godot.replication.transport.TransportLog
import ch.hippmann.godot.replication.sync.NodeRegistry
import godot.api.Engine
import godot.api.Node
import godot.api.PackedScene
import godot.api.SceneTree
import godot.core.asStringName
import godot.coroutines.awaitNextFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

object Network {
    private val mutableState = MutableStateFlow<NetworkState>(NetworkState.Offline)
    private val mutableSession = MutableStateFlow<SessionView?>(null)
    private val mutableLobby = MutableStateFlow(LobbyState.EMPTY)
    private val mutableLevel = MutableStateFlow(LevelState.NONE)
    private val mutableStatistics = MutableStateFlow(NetworkStatistics())
    private val mutableEvents = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = EVENT_BUFFER)
    private var managerRequested = false

    var configuration: NetworkConfiguration = NetworkConfiguration()
        private set

    val state: StateFlow<NetworkState> = mutableState
    val session: StateFlow<SessionView?> = mutableSession
    val lobby: StateFlow<LobbyState> = mutableLobby
    val level: StateFlow<LevelState> = mutableLevel
    val statistics: StateFlow<NetworkStatistics> = mutableStatistics

    /** Latency, jitter and loss applied to this process; changes take effect at once, also mid session. */
    var simulation: NetworkSimulation? = null
        set(value) {
            field = value
            ReplicationManager.instance?.runtime?.transport?.simulation = value?.toConditions()
        }
    val events: SharedFlow<NetworkEvent> = mutableEvents

    val localPlayerId: PlayerId
        get() = session.value?.localPlayerId ?: PlayerId.NONE

    val master: PlayerId
        get() = session.value?.master ?: PlayerId.NONE

    val isMaster: Boolean
        get() = session.value?.isMaster == true

    val isManagerInstalled: Boolean
        get() = ReplicationManager.instance != null

    fun configure(block: NetworkConfiguration.() -> Unit) {
        check(state.value == NetworkState.Offline) { "The network can only be configured while offline" }
        configuration = configuration.copy().apply(block)
        TransportLog.enabled = configuration.verboseTransportLogging
    }

    /** [online] registers the session with [NetworkConfiguration.rendezvousUrl] so players anywhere can join by [sessionCode]. */
    suspend fun host(lobby: LobbyConfiguration, profile: PlayerProfile, port: Int = configuration.port, online: Boolean = false): SessionView {
        val runtime = startRuntime()
        try {
            runtime.host(lobby, profile, port, online)
        } catch (failure: Exception) {
            runtime.manager.endSession()
            throw failure
        }
        return checkNotNull(session.value)
    }

    /** Joins a session registered with the configured rendezvous service, punching through NATs or falling back to its relay. */
    suspend fun joinByCode(code: String, profile: PlayerProfile, password: String? = null): SessionView {
        val runtime = startRuntime()
        try {
            runtime.joinByCode(code, profile, password)
        } catch (failure: Exception) {
            runtime.manager.endSession()
            throw failure
        }
        return checkNotNull(session.value)
    }

    /** How the link to [player] was made ("direct", "punchthrough", "relay"), null while there is none. */
    fun connectionStrategyOf(player: PlayerId): String? =
        ReplicationManager.instance?.runtime?.transport?.linkFor(player)?.strategy?.takeIf { name -> name.isNotEmpty() }

    /** The code others can join with while this session is registered with a rendezvous service. */
    val sessionCode: String?
        get() = ReplicationManager.instance?.runtime?.online?.code?.takeIf { code -> code.isNotEmpty() }

    suspend fun join(address: String, port: Int, profile: PlayerProfile, password: String? = null): SessionView {
        val runtime = startRuntime()
        try {
            runtime.join(address, port, profile, password)
        } catch (failure: Exception) {
            runtime.manager.endSession()
            throw failure
        }
        return checkNotNull(session.value)
    }

    suspend fun leave() {
        val manager = ReplicationManager.instance ?: return
        val runtime = manager.runtime ?: return
        runtime.leave(LeaveReason.GRACEFUL)
        manager.endSession()
    }

    fun setReady(ready: Boolean): Unit = runtime().lobbyService.setReady(ready)

    fun updateProfile(block: PlayerProfile.() -> PlayerProfile) {
        val runtime = runtime()
        runtime.lobbyService.updateProfile(runtime.localProfile.block())
    }

    /** Master only; other members forward the change to the master through [requestLobbyUpdate]. */
    fun updateLobby(block: LobbyConfiguration.() -> LobbyConfiguration) {
        check(isMaster) { "Only the master can update the lobby; use requestLobbyUpdate from other members" }
        val runtime = runtime()
        runtime.lobbyService.updateLobby(runtime.lobbyConfiguration.block())
    }

    fun requestLobbyUpdate(block: LobbyConfiguration.() -> LobbyConfiguration) {
        val runtime = runtime()
        runtime.lobbyService.updateLobby(runtime.lobbyConfiguration.block())
    }

    fun kick(player: PlayerId, reason: String = "kicked") {
        check(isMaster) { "Only the master can kick" }
        runtime().lobbyService.kick(player, reason)
    }

    /** Master only; other members forward the request to the master. Every member loads the scene under [NetworkConfiguration.levelParentPath]. */
    fun loadLevel(scenePath: String, policy: LevelPolicy = LevelPolicy.WaitForAll()): Unit = runtime().levelService.loadLevel(scenePath, policy)

    val levelNode: Node?
        get() = ReplicationManager.instance?.runtime?.levelService?.levelNode

    fun <T : Node> spawn(scene: PackedScene, parent: Node, owner: PlayerId = localPlayerId, spawnData: Any? = null, options: SpawnOptions = SpawnOptions()): T =
        runtime().replication.spawnService.spawn(scene, parent, owner, spawnData, options)

    fun despawn(node: Node): Unit = runtime().replication.spawnService.despawn(node)

    fun ownerOf(node: Node): PlayerId = NodeRegistry.replicaOf(node)?.owner ?: PlayerId.NONE

    fun isOwner(node: Node): Boolean = NodeRegistry.replicaOf(node)?.isOwnedLocally == true

    /** The current owner or the master hands [node] to [to]; the new owner resends its full state on the next tick. */
    fun transferOwnership(node: Node, to: PlayerId): Unit = runtime().replication.ownership.transfer(node, to)

    /** Asks the owner; granted at once for [OwnershipPolicy.Transferable], through the owner's callback for [OwnershipPolicy.RequestRequired]. */
    suspend fun requestOwnership(node: Node, timeoutMilliseconds: Long = OWNERSHIP_REQUEST_TIMEOUT_MILLISECONDS): Boolean =
        runtime().replication.ownership.request(node, timeoutMilliseconds)

    inline fun <reified T : Any> send(payload: T, target: Target = Target.All, reliable: Boolean = true): Unit =
        send(serializer<T>(), payload, target, reliable)

    fun <T> send(serializer: KSerializer<T>, payload: T, target: Target, reliable: Boolean) {
        val runtime = runtime()
        runtime.customMessages.send(runtime.transport, serializer, payload, target.resolve() - localPlayerId, reliable)
    }

    /** Messages of type [T] from other members, across sessions; subscribe before they are sent, the flow keeps no history. */
    inline fun <reified T : Any> messages(): Flow<Received<T>> = messages(serializer<T>())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> messages(serializer: KSerializer<T>): Flow<Received<T>> = state.flatMapLatest { current ->
        if (current == NetworkState.Connected) runtime().customMessages.messages(serializer) else emptyFlow()
    }

    suspend fun discoverLocalSessions(timeoutMilliseconds: Long = DISCOVERY_TIMEOUT_MILLISECONDS): List<DiscoveredSession> =
        LanDiscovery(configuration.discoveryPort).discover(timeoutMilliseconds)

    /**
     * Adds the manager node under the root on the next idle frame. A direct `addChild` fails when called while the
     * root is still setting up its children, which is the case for every `_ready` of the main scene.
     */
    fun ensureManager() {
        if (managerRequested || isManagerInstalled) return
        val tree = Engine.getMainLoop() as? SceneTree ?: error("The replication library needs a SceneTree main loop")
        managerRequested = true
        tree.root.callDeferred("add_child".asStringName(), ReplicationManager())
    }

    suspend fun awaitManager(): ReplicationManager {
        ensureManager()
        while (true) {
            ReplicationManager.instance?.let { return it }
            awaitNextFrame()
        }
    }

    internal fun setState(newState: NetworkState) {
        mutableState.value = newState
    }

    internal fun updateSession(view: SessionView?) {
        mutableSession.value = view
    }

    internal fun updateLobby(state: LobbyState) {
        mutableLobby.value = state
    }

    internal fun updateLevel(state: LevelState) {
        mutableLevel.value = state
    }

    internal fun updateStatistics(statistics: NetworkStatistics) {
        mutableStatistics.value = statistics
    }

    @PublishedApi
    internal fun runtime(): SessionRuntime =
        ReplicationManager.instance?.runtime?.takeIf { state.value == NetworkState.Connected } ?: error("Not in a session")

    internal fun emit(event: NetworkEvent) {
        mutableEvents.tryEmit(event)
    }

    private suspend fun startRuntime(): SessionRuntime {
        check(state.value == NetworkState.Offline) { "Leave the current session before starting another one" }
        val manager = awaitManager()
        if (manager.runtime != null) manager.endSession()
        return manager.startSession(configuration)
    }

    private const val EVENT_BUFFER = 64
    private const val DISCOVERY_TIMEOUT_MILLISECONDS = 2_000L
    private const val OWNERSHIP_REQUEST_TIMEOUT_MILLISECONDS = 5_000L
}
