package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.sync.spawnData
import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Node3D
import godot.core.Vector3
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class Orbit(val centerX: Double, val centerZ: Double, val phase: Double)

/** Circles its orbit center on the owner; everyone else only sees the interpolated position stream. */
@Script
class Drone : Node3D() {
    val motion = synced(::position) { unreliable(); continuous(); interpolate() }
    val orbit by spawnData<Orbit>()

    private var elapsed = 0.0

    override fun _physicsProcess(delta: Double) {
        if (!Network.isOwner(this)) return
        elapsed += delta
        val angle = elapsed * ANGULAR_SPEED + orbit.phase
        position = Vector3(orbit.centerX + cos(angle) * RADIUS, 0.0, orbit.centerZ + sin(angle) * RADIUS)
    }

    companion object {
        const val RADIUS = 3.0
        const val ANGULAR_SPEED = 1.5
    }
}
