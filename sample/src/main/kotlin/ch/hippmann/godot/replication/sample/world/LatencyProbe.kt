package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Node3D

/** Carries wall clock stamps over the three property paths so a receiver can measure each one's latency. */
@Script
class LatencyProbe : Node3D() {
    var reliableStamp by synced(0L)
    var streamStamp by synced(0L) { unreliable(); continuous() }
    val motion = synced(::position) { unreliable(); continuous(); interpolate() }

    companion object {
        /** Positions encode seconds since the hour, so a receiver can read the sender's clock out of the interpolated value. */
        const val UNITS_PER_SECOND = 1.0
        const val HOUR_MILLISECONDS = 3_600_000L

        fun hourStart(nowMilliseconds: Long): Long = nowMilliseconds / HOUR_MILLISECONDS * HOUR_MILLISECONDS
    }
}
