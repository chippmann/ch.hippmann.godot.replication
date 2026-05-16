package ch.hippmann.godot.replication.it.fixtures

import ch.hippmann.godot.replication.Replicated
import ch.hippmann.godot.replication.Replicator
import godot.annotation.RegisterClass
import godot.annotation.RegisterFunction
import godot.api.Node

/**
 * Concrete Replicator wired up the way the README documents:
 * call [initReplication] from `_enterTree` so the delegate's signal connections
 * are in place before any peer subscribes.
 *
 * SimpleReplicator in the library doesn't override `_enterTree`, so it never
 * actually initializes — usable only as a Kotlin marker, not at runtime.
 */
@RegisterClass
class ITReplicator : Node(), Replicated by Replicator() {
    @RegisterFunction
    override fun _enterTree() {
        initReplication()
    }
}
