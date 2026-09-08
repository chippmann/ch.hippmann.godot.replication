package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Label3D
import godot.api.Node3D
import godot.core.Vector3
import godot.extension.api.getNodeAs

@Script
class Crate : Node3D() {
    var pushes by synced(0)
    var label by synced("")
    val motion = synced(::position) { interpolate() }

    private var tag: Label3D? = null

    override fun _ready() {
        tag = getNodeAs<Label3D>("Tag")
        all += this
    }

    override fun _exitTree() {
        all -= this
    }

    override fun _process(delta: Double) {
        val tag = tag ?: return
        val owner = Network.ownerOf(this).value
        tag.text = "${label.ifBlank { name.toString() }}\npushed ${pushes}x, owner $owner"
    }

    /** Only the owner moves the crate; [Interactions] takes ownership first when needed. */
    fun push(direction: Vector3) {
        pushes++
        position += direction * PUSH_DISTANCE
    }

    companion object {
        const val PUSH_DISTANCE = 1.5
        val all = HashSet<Crate>()
    }
}
