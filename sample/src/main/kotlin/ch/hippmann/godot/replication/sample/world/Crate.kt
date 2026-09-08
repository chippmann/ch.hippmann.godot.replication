package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Node3D

@Script
class Crate : Node3D() {
    var pushes by synced(0)
    var label by synced("")
}
