package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.sample.game.GameLog
import godot.api.Node
import godot.api.PackedScene
import godot.core.Vector3
import godot.coroutines.launch
import godot.global.GD

/** What the local player does with the world: shooting and taking crates and doors over before using them. */
class Interactions(private val player: Player) {
    private val projectileScene: PackedScene? by lazy { GD.load<PackedScene>("res://scenes/projectile.tscn") }

    fun shoot() {
        val scene = projectileScene ?: return
        val parent = player.getParent() ?: return
        val origin = player.position + Vector3(0.0, 1.0, 0.0) + player.facing * 0.8
        Network.spawn<Projectile>(scene, parent, spawnData = Shot(origin, player.facing))
    }

    fun interact() {
        val crate = Crate.all.filter { crate -> crate.position.distanceTo(player.position) < REACH }.minByOrNull { crate -> crate.position.distanceTo(player.position) }
        if (crate != null) {
            withOwnership(crate, "crate ${crate.name}") { crate.push(player.facing) }
            return
        }
        val door = Door.all.firstOrNull { door -> door.position.distanceTo(player.position) < DOOR_REACH }
        if (door != null) {
            withOwnership(door, "door ${door.name}") { door.open = !door.open }
            return
        }
        GameLog.note("Nothing to interact with here")
    }

    private fun withOwnership(node: Node, description: String, action: () -> Unit) {
        if (Network.isOwner(node)) {
            action()
            return
        }
        player.launch {
            GameLog.note("Asking ${Network.ownerOf(node).value} for $description")
            if (Network.requestOwnership(node)) {
                GameLog.note("Took over $description")
                action()
            } else {
                GameLog.note("Owner refused $description")
            }
        }
    }

    private companion object {
        const val REACH = 2.5
        const val DOOR_REACH = 4.0
    }
}
