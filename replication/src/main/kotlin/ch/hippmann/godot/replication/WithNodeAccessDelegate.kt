package ch.hippmann.godot.replication

import godot.Node
import java.lang.ref.WeakReference

class WithNodeAccessDelegate: WithNodeAccess {
    override lateinit var thisNode: WeakReference<Node>

    override fun <T : Node> T.initNodeAccess() {
        thisNode = WeakReference(this)
    }
}
