package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.JoinFailure
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkConfiguration
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.SessionView
import ch.hippmann.godot.replication.core.level.LevelState
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.Membership
import ch.hippmann.godot.replication.core.session.PasswordVerifier
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.Custom
import ch.hippmann.godot.replication.core.wire.Despawn
import ch.hippmann.godot.replication.diagnostics.NetworkStatistics
import ch.hippmann.godot.replication.rpc.CustomMessages
import ch.hippmann.godot.replication.sync.FrameClock
import ch.hippmann.godot.replication.sync.NodeRegistry
import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.transport.ConnectionStrategy
import ch.hippmann.godot.replication.transport.DirectStrategy
import ch.hippmann.godot.replication.transport.EnetLink
import ch.hippmann.godot.replication.sync.ReplicationRuntime
import ch.hippmann.godot.replication.transport.LanDiscovery
import ch.hippmann.godot.replication.transport.ReplicationMeshPeer
import ch.hippmann.godot.replication.transport.Transport
import ch.hippmann.godot.replication.transport.TransportListener
import godot.api.MultiplayerPeer
import godot.api.SceneMultiplayer
import godot.global.GD

/** Everything one session owns; created on host or join and discarded on leave. */
internal class SessionRuntime(
    val manager: ReplicationManager,
    val configuration: NetworkConfiguration,
) : TransportListener {
    val transport = Transport(configuration, this).also { transport -> transport.simulation = Network.simulation?.toConditions() }
    val meshPeer = ReplicationMeshPeer().also { peer -> peer.transport = transport }
    val mailbox = LinkMailbox(transport::defer)
    val handshake = HandshakeHandler(this)
    val membershipHandler = MembershipHandler(this)
    val lobbyService = LobbyService(this)
    val levelService = LevelService(this)
    val discovery = LanDiscovery(configuration.discoveryPort)
    val replication = ReplicationRuntime(this)
    val customMessages = CustomMessages()
    val strategies: List<ConnectionStrategy> = listOf(DirectStrategy)

    var masterRole: MasterRole? = null
    var membership: Membership? = null
    var localPlayerId: PlayerId = PlayerId.NONE
    var localProfile: PlayerProfile = PlayerProfile(name = "")
    var lobbyConfiguration: LobbyConfiguration = LobbyConfiguration(name = "")
    var levelState: LevelState = LevelState.NONE
    var passwordVerifier: PasswordVerifier? = null

    val isMaster: Boolean
        get() = membership?.master == localPlayerId

    fun sceneMultiplayer(): SceneMultiplayer =
        manager.getTree()?.getMultiplayer() as? SceneMultiplayer ?: error("The scene tree has no SceneMultiplayer")

    fun installMeshPeer() {
        meshPeer.localPlayerId = localPlayerId
        meshPeer.status = MultiplayerPeer.ConnectionStatus.CONNECTED
        val multiplayer = sceneMultiplayer()
        multiplayer.serverRelay = false
        multiplayer.multiplayerPeer = meshPeer
    }

    fun removeMeshPeer() {
        val multiplayer = sceneMultiplayer()
        if (multiplayer.multiplayerPeer === meshPeer) multiplayer.multiplayerPeer = null
        meshPeer.status = MultiplayerPeer.ConnectionStatus.DISCONNECTED
    }

    fun publishSession() {
        val membership = membership ?: return
        val view = SessionView.of(localPlayerId, membership, transport.connectedPlayers)
        val level = levelState
        transport.defer {
            Network.updateSession(view)
            Network.updateLevel(level)
        }
        lobbyService.publish()
    }

    fun pump() {
        transport.pump()
        discovery.pump()
    }

    fun physicsStep() {
        if (Network.state.value == NetworkState.Connected) replication.physicsStep()
    }

    fun frame() {
        if (Network.state.value == NetworkState.Connected) replication.frame()
        if (transport.counters.advance(FrameClock.nowMilliseconds)) publishStatistics()
    }

    private fun publishStatistics() {
        val counters = transport.counters
        val roundTrips = HashMap<PlayerId, Double>()
        val losses = HashMap<PlayerId, Double>()
        for (link in transport.links) {
            val player = link.player ?: continue
            roundTrips[player] = link.roundTripTime
            losses[player] = link.packetLoss
        }
        Network.updateStatistics(
            NetworkStatistics(
                packetsIn = counters[NetworkStatistics.PACKETS_IN],
                packetsOut = counters[NetworkStatistics.PACKETS_OUT],
                bytesIn = counters[NetworkStatistics.BYTES_IN],
                bytesOut = counters[NetworkStatistics.BYTES_OUT],
                statePacketsOut = counters[NetworkStatistics.STATE_PACKETS_OUT],
                reliableStatePacketsOut = counters[NetworkStatistics.RELIABLE_STATE_PACKETS_OUT],
                ticks = counters[NetworkStatistics.TICKS],
                activeReplicas = NodeRegistry.active.size,
                droppedEntries = counters[NetworkStatistics.DROPPED_ENTRIES],
                processingMilliseconds = counters[NetworkStatistics.PROCESSING_MICROSECONDS] / 1_000.0,
                roundTripMilliseconds = roundTrips,
                packetLoss = losses,
            ),
        )
    }

    fun emit(event: NetworkEvent) {
        transport.defer { Network.emit(event) }
    }

    fun onMemberAdded(member: MemberRecord) {
        publishSession()
        emit(NetworkEvent.MemberJoined(member))
    }

    override fun onInboundLink(link: EnetLink): Unit = handshake.onInboundLink(link)

    override fun onLinkClosed(link: EnetLink) {
        mailbox.fail(link, JoinFailure.LinkLost("The link to $link was lost"))
        handshake.onLinkClosed(link)
        membershipHandler.onLinkClosed(link)
    }

    override fun onControlMessage(link: EnetLink, message: WireMessage) {
        if (mailbox.deliver(link, message)) return
        if (handshake.onMessage(link, message)) return
        if (membershipHandler.onMessage(link, message)) return
        if (lobbyService.onMessage(link, message)) return
        if (levelService.onMessage(link, message)) return
        val sender = link.player
        if (sender != null && message is Despawn) {
            replication.onDespawn(sender, message)
            return
        }
        if (sender != null && replication.ownership.onMessage(sender, message)) return
        if (sender != null && replication.snapshot.onMessage(sender, message)) return
        if (sender != null && message is Custom) {
            customMessages.onMessage(sender, message)
            return
        }
        GD.printVerbose("Replication: unhandled ${message.type} from $link")
    }

    override fun onStatePacket(link: EnetLink, type: MessageType, bytes: ByteArray) {
        val sender = link.player ?: return
        replication.onStatePacket(sender, type, bytes)
    }

    override fun onRpcPacket(link: EnetLink, channel: Int, bytes: ByteArray) {
        val player = link.player ?: return
        meshPeer.enqueue(player, channel, bytes)
    }
}
