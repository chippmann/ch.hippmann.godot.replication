package ch.hippmann.godot.replication

import godot.Node
import java.lang.ref.WeakReference

interface WithNodeAccess {
    var thisNode: WeakReference<Node>

    fun <T> T.initNodeAccess() where T : Node

    @Suppress("UNCHECKED_CAST")
    fun <T> T.thisNodeAsType() = thisNode.get() as T
}

inline fun WithNodeAccess.ifAuthority(block: Node.() -> Unit) {
    val node = thisNode.get() ?: return
    if (node.isMultiplayerAuthority()) {
        block(node)
    }
}

inline fun WithNodeAccess.ifPeer(block: Node.() -> Unit) {
    val node = thisNode.get() ?: return
    if (!node.isMultiplayerAuthority()) {
        block(node)
    }
}
