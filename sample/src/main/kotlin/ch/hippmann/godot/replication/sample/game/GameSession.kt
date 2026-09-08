package ch.hippmann.godot.replication.sample.game

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.sample.world.Loadout
import ch.hippmann.godot.replication.sample.world.Player
import godot.annotation.Script
import godot.api.InputEvent
import godot.api.Node
import godot.api.Node3D
import godot.api.PackedScene
import godot.core.NodePath
import godot.coroutines.launch
import godot.global.GD
import kotlinx.coroutines.flow.collect

/** Puts the local player into every level that starts and takes it out again when the session ends. */
@Script
class GameSession : Node() {
    val hud = Hud()
    var localPlayer: Player? = null
        private set
    private var spawnedSequence = 0

    override fun _ready() {
        addChild(hud)
        hud.visible = false
        launch { Network.state.collect { state -> if (state != NetworkState.Connected) reset() } }
        launch { Network.events.collect { event -> onEvent(event) } }
        launch { Network.messages(HitMessage.serializer()).collect { received -> onHit(received.payload) } }
    }

    val inLevel: Boolean
        get() = localPlayer != null

    private fun onEvent(event: NetworkEvent) {
        when (event) {
            is NetworkEvent.LevelLoaded -> if (Network.level.value.started) spawnLocalPlayer(event.level, event.sequence)
            is NetworkEvent.LevelStarted -> Network.levelNode?.let { level -> spawnLocalPlayer(level, event.sequence) }
            is NetworkEvent.MemberJoined -> GameLog.note("${event.member.profile.name} joined")
            is NetworkEvent.MemberLeft -> GameLog.note("Player ${event.player.value} left (${event.reason})")
            is NetworkEvent.MasterChanged -> GameLog.note("Player ${event.master.value} is now the master")
            is NetworkEvent.OwnershipChanged -> GameLog.note("${event.node.name} now belongs to ${event.newOwner.value}")
            else -> Unit
        }
    }

    private fun spawnLocalPlayer(level: Node, sequence: Int) {
        if (spawnedSequence == sequence) return
        spawnedSequence = sequence
        val scene = GD.load<PackedScene>("res://scenes/player.tscn") ?: return
        val spawns = level.getNodeOrNull(NodePath("Spawns"))?.getChildren()?.filterIsInstance<Node3D>().orEmpty()
        val members = Network.lobby.value.players.map { player -> player.id }.sortedBy { id -> id.value }
        val slot = members.indexOf(Network.localPlayerId).coerceAtLeast(0)
        val point = spawns.getOrNull(slot % maxOf(1, spawns.size))?.position ?: Node3DPositions.fallback(slot)
        val profile = Network.lobby.value.players.firstOrNull { player -> player.id == Network.localPlayerId }?.profile
        localPlayer = Network.spawn<Player>(scene, level, spawnData = Loadout("blaster", 0, point.x, point.z)).also { player ->
            player.displayName = profile?.name ?: "Player ${Network.localPlayerId.value}"
        }
        hud.visible = true
        GameLog.note("Entered ${level.name} as ${Network.localPlayerId.value}")
    }

    private fun onHit(hit: HitMessage) {
        val player = localPlayer ?: return
        player.takeDamage(hit.damage)
        GameLog.note(if (player.health == 100) "Player ${hit.shooter} got you, respawned" else "Hit by ${hit.shooter}, health ${player.health}")
    }

    private fun reset() {
        localPlayer = null
        spawnedSequence = 0
        hud.visible = false
        GameLog.clear()
    }

    override fun _unhandledInput(event: InputEvent) {
        if (event.isActionPressed("ui_cancel") && Network.state.value == NetworkState.Connected) {
            launch { Network.leave() }
        }
    }
}

private object Node3DPositions {
    fun fallback(slot: Int): godot.core.Vector3 = godot.core.Vector3(-6.0 + slot * 4.0, 0.0, 6.0)
}
