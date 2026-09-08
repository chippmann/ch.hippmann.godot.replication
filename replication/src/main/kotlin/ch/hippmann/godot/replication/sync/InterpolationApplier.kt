package ch.hippmann.godot.replication.sync

internal class InterpolationApplier {
    fun frame() {
        val now = FrameClock.nowMilliseconds
        for (replica in NodeRegistry.active) {
            if (replica.isOwnedLocally) continue
            for (property in replica.properties) {
                if (property is EnginePropertyBinding && property.buffer != null) {
                    property.applyInterpolated(property.renderTime(now), replica.maximumExtrapolationMilliseconds)
                }
            }
        }
    }
}
