package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.session.PlayerId
import godot.api.Node
import godot.core.Vector3

/** Decides which viewers receive a node's state stream; spawns and despawns always reach everyone. */
fun interface InterestFilter {
    fun includes(node: Node, viewer: PlayerId): Boolean

    object Always : InterestFilter {
        override fun includes(node: Node, viewer: PlayerId): Boolean = true
    }

    /** Viewers whose position is unknown are included, so a filter never hides a node by accident. */
    class Distance(
        private val range: Double,
        private val positionOf: (Node) -> Vector3,
        private val viewerPositionOf: (PlayerId) -> Vector3?,
    ) : InterestFilter {
        override fun includes(node: Node, viewer: PlayerId): Boolean {
            val viewerPosition = viewerPositionOf(viewer) ?: return true
            return positionOf(node).distanceTo(viewerPosition) <= range
        }
    }
}
