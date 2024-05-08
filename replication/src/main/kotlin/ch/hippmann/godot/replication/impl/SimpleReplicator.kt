package ch.hippmann.godot.replication.impl

import ch.hippmann.godot.replication.Replicated
import ch.hippmann.godot.replication.Replicator
import godot.Node
import godot.annotation.RegisterClass

@RegisterClass
class SimpleReplicator: Node(), Replicated by Replicator()