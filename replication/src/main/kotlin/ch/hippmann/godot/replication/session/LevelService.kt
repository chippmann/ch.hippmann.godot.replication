package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.core.level.LevelState
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.LevelLoad
import ch.hippmann.godot.replication.core.wire.LevelLoaded
import ch.hippmann.godot.replication.core.wire.LevelStart
import ch.hippmann.godot.replication.core.wire.LobbyCommand
import ch.hippmann.godot.replication.core.wire.LobbyCommandPayload
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.transport.EnetLink
import godot.api.Node
import godot.api.PackedScene
import godot.api.ResourceLoader
import godot.core.NodePath
import godot.core.asStringName
import godot.coroutines.awaitLoadAs
import godot.coroutines.launch
import godot.global.GD
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * Every member loads the level the master announced under the configured parent; with [LevelPolicy.WaitForAll]
 * the level stays disabled until the master saw every member's load report or the straggler timeout passed.
 */
internal class LevelService(private val runtime: SessionRuntime) {
    private var loadedReports = HashSet<PlayerId>()
    private var stragglerTimer: Job? = null
    private var loading: Job? = null
    private var loadedSequence = 0

    var levelNode: Node? = null
        private set

    fun loadLevel(scenePath: String, policy: LevelPolicy) {
        val membership = runtime.membership ?: return
        if (!runtime.isMaster) {
            runtime.transport.sendMessage(membership.master, LobbyCommand(LobbyCommandPayload.LoadLevel(scenePath, policy)))
            return
        }
        val state = LevelState(runtime.levelState.sequence + 1, scenePath, policy, started = false, loaded = emptySet())
        runtime.levelState = state
        loadedReports = HashSet()
        runtime.transport.broadcastMessage(LevelLoad(membership.epoch, state.sequence, scenePath, policy))
        armStragglerTimer(state)
        follow(state)
    }

    fun onMessage(link: EnetLink, message: WireMessage): Boolean {
        val sender = link.player ?: return false
        val membership = runtime.membership ?: return false
        when (message) {
            is LevelLoad -> if (sender == membership.master && message.levelSequence > runtime.levelState.sequence) {
                runtime.levelState = LevelState(message.levelSequence, message.scenePath, message.policy, started = false, loaded = emptySet())
                runtime.publishSession()
                follow(runtime.levelState)
            }
            is LevelLoaded -> if (runtime.isMaster && message.levelSequence == runtime.levelState.sequence) onLoadedReport(sender)
            is LevelStart -> if (sender == membership.master && message.levelSequence == runtime.levelState.sequence) startLocally()
            else -> return false
        }
        return true
    }

    /** A late joiner loads what the master announced before requesting the world snapshot. */
    suspend fun followAdmittedLevel() {
        loading?.join()
        val state = runtime.levelState
        if (state.scenePath == null || loadedSequence == state.sequence) return
        load(state)
    }

    fun onMasterChanged() {
        val state = runtime.levelState
        if (runtime.isMaster && state.scenePath != null && !state.started) {
            loadedReports = HashSet(state.loaded)
            armStragglerTimer(state)
        }
    }

    fun unload() {
        stragglerTimer?.cancel()
        loading?.cancel()
        levelNode?.queueFree()
        levelNode = null
        loadedSequence = 0
    }

    private fun follow(state: LevelState) {
        loading?.cancel()
        loading = runtime.manager.launch { load(state) }
    }

    private suspend fun load(state: LevelState) {
        val scenePath = state.scenePath ?: return
        if (loadedSequence == state.sequence) return
        levelNode?.queueFree()
        levelNode = null
        val scene = ResourceLoader.awaitLoadAs<PackedScene>(scenePath)
        val node = scene?.instantiate()
        if (node == null) {
            GD.printErr("Replication: cannot load level $scenePath")
            return
        }
        if (runtime.levelState.sequence != state.sequence) {
            node.queueFree()
            return
        }
        node.name = LEVEL_NODE_NAME.asStringName()
        val startNow = state.policy is LevelPolicy.StartWhenLoaded || state.started
        if (!startNow) node.processMode = Node.ProcessMode.DISABLED
        val parent = runtime.manager.getTree()?.root?.getNodeOrNull(NodePath(runtime.configuration.levelParentPath))
            ?: error("Level parent ${runtime.configuration.levelParentPath} does not exist")
        parent.addChild(node)
        levelNode = node
        loadedSequence = state.sequence
        runtime.configuration.levelPreparation?.invoke(node)
        if (runtime.levelState.sequence != state.sequence) return

        // Report and start before the event so a handler already sees the final level state.
        if (runtime.isMaster) onLoadedReport(runtime.localPlayerId) else runtime.transport.sendMessage(runtime.membership?.master ?: return, LevelLoaded(state.sequence))
        if (startNow) startLocally()
        runtime.emit(NetworkEvent.LevelLoaded(node, state.sequence))
    }

    private fun onLoadedReport(player: PlayerId) {
        val membership = runtime.membership ?: return
        loadedReports += player
        runtime.levelState = runtime.levelState.copy(loaded = loadedReports.toSet())
        runtime.publishSession()
        if (runtime.levelState.started) return
        val everyoneLoaded = membership.ids.all { id -> id in loadedReports }
        if (everyoneLoaded || runtime.levelState.policy is LevelPolicy.StartWhenLoaded) startEveryone()
    }

    private fun armStragglerTimer(state: LevelState) {
        stragglerTimer?.cancel()
        val policy = state.policy as? LevelPolicy.WaitForAll ?: return
        stragglerTimer = runtime.manager.launch {
            delay(policy.stragglerTimeoutMilliseconds)
            if (runtime.isMaster && runtime.levelState.sequence == state.sequence && !runtime.levelState.started) startEveryone()
        }
    }

    private fun startEveryone() {
        val membership = runtime.membership ?: return
        stragglerTimer?.cancel()
        runtime.transport.broadcastMessage(LevelStart(membership.epoch, runtime.levelState.sequence))
        startLocally()
    }

    private fun startLocally() {
        if (runtime.levelState.started && levelNode?.processMode != Node.ProcessMode.DISABLED) return
        runtime.levelState = runtime.levelState.copy(started = true)
        levelNode?.processMode = Node.ProcessMode.INHERIT
        runtime.publishSession()
        runtime.emit(NetworkEvent.LevelStarted(runtime.levelState.sequence))
    }

    private companion object {
        const val LEVEL_NODE_NAME = "Level"
    }
}
