package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Node3D
import godot.core.Vector3

@Script
class Projectile : Node3D() {
    var velocity by synced(Vector3.ZERO)
    var shooter by synced(0)
}
