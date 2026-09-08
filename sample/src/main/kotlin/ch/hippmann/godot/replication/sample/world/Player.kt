package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkConfigured
import ch.hippmann.godot.replication.NodeNetworkConfiguration
import ch.hippmann.godot.replication.sync.InterestFilter
import ch.hippmann.godot.replication.sync.spawnData
import ch.hippmann.godot.replication.sync.synced
import ch.hippmann.godot.replication.transport.TransportLog
import godot.annotation.Script
import godot.api.Camera3D
import godot.api.Input
import godot.api.InputEvent
import godot.api.Label3D
import godot.api.MeshInstance3D
import godot.api.Node3D
import godot.api.StandardMaterial3D
import godot.core.Color
import godot.core.Vector3
import godot.extension.api.getNodeAs

@Script
class Player : Node3D(), NetworkConfigured {
    var health by synced(100)
    var displayName by synced("")
    val positionSync = synced(::position) { unreliable(); continuous(rate = 30); interpolate() }
    val loadout by spawnData<Loadout>()

    /** Scenarios drive movement through this; humans use the input map. */
    var speed: Double = 0.0
    var facing: Vector3 = Vector3(0.0, 0.0, -1.0)
    var spawnPoint: Vector3 = Vector3.ZERO

    private var nameTag: Label3D? = null
    private val interactions = Interactions(this)

    override fun _ready() {
        position = Vector3(loadout.startX, 0.0, loadout.startZ)
        spawnPoint = position
        byOwner[Network.ownerOf(this).value] = this
        nameTag = getNodeAs<Label3D>("NameTag")
        getNodeAs<MeshInstance3D>("Body")?.materialOverride = StandardMaterial3D().apply { albedoColor = colorOf(Network.ownerOf(this@Player).value) }
        getNodeAs<Camera3D>("Camera")?.current = Network.isOwner(this)
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

    override fun _process(delta: Double) {
        nameTag?.text = "$displayName  $health"
    }

    override fun _physicsProcess(delta: Double) {
        if (!Network.isOwner(this)) return
        if (speed != 0.0) position = position + Vector3(speed * delta, 0.0, 0.0)
        val input = Input.getVector("move_left", "move_right", "move_forward", "move_back")
        if (input.length() > 0.01) {
            facing = Vector3(input.x, 0.0, input.y).normalized()
            position = position + facing * (WALK_SPEED * delta)
        }
    }

    override fun _unhandledInput(event: InputEvent) {
        if (!Network.isOwner(this)) return
        when {
            event.isActionPressed("shoot") -> interactions.shoot()
            event.isActionPressed("interact") -> interactions.interact()
        }
    }

    fun takeDamage(damage: Int) {
        health -= damage
        if (health <= 0) {
            health = 100
            position = spawnPoint
        }
    }

    companion object {
        const val WALK_SPEED = 7.0

        /** Set before spawning to limit who receives a player's state stream. */
        var interestRange: Double? = null
        val byOwner = HashMap<Int, Player>()

        private val palette = listOf(
            Color(0.93, 0.36, 0.33),
            Color(0.36, 0.62, 0.95),
            Color(0.4, 0.82, 0.45),
            Color(0.95, 0.76, 0.28),
            Color(0.72, 0.45, 0.9),
            Color(0.3, 0.8, 0.8),
        )

        fun colorOf(ownerId: Int): Color = palette[Math.floorMod(ownerId, palette.size)]
    }
}
