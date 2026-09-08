package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkConfigured
import ch.hippmann.godot.replication.NodeNetworkConfiguration
import ch.hippmann.godot.replication.sync.InterestFilter
import ch.hippmann.godot.replication.sync.spawnData
import ch.hippmann.godot.replication.sync.synced
import ch.hippmann.godot.replication.transport.TransportLog
import godot.annotation.Script
import godot.api.Node3D
import godot.core.Vector3

@Script
class Player : Node3D(), NetworkConfigured {
    var health by synced(100)
    var displayName by synced("")
    val positionSync = synced(::position) { unreliable(); continuous(rate = 30); interpolate() }
    val loadout by spawnData<Loadout>()

    var speed: Double = 0.0

    override fun _ready() {
        position = Vector3(loadout.startX, 0.0, 0.0)
        byOwner[Network.ownerOf(this).value] = this
    }

    override fun _exitTree() {
        byOwner.remove(Network.ownerOf(this).value)
    }

    override fun configureNetwork(configuration: NodeNetworkConfiguration) {
        val range = interestRange ?: return
        configuration.interestFilter = InterestFilter.Distance(
            range = range,
            positionOf = { node -> (node as Node3D).position },
            viewerPositionOf = { viewer ->
                val viewerPosition = byOwner[viewer.value]?.position
                TransportLog.log { "viewer ${viewer.value} position $viewerPosition for ${name} at $position (known owners ${byOwner.keys})" }
                viewerPosition
            },
        )
    }

    override fun _physicsProcess(delta: Double) {
        if (Network.isOwner(this) && speed != 0.0) {
            position = position + Vector3(speed * delta, 0.0, 0.0)
        }
    }

    companion object {
        /** Set before spawning to limit who receives a player's state stream. */
        var interestRange: Double? = null
        val byOwner = HashMap<Int, Player>()
    }
}
