package ch.hippmann.godot.replication.sample.world

import godot.annotation.Script
import godot.api.Node3D

@Script
class Hangar : Node3D() {
    var readyFrames: Int = 0

    override fun _process(delta: Double) {
        readyFrames++
    }
}
