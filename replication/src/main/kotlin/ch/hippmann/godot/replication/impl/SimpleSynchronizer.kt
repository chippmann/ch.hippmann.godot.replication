package ch.hippmann.godot.replication.impl

import ch.hippmann.godot.replication.Synchronized
import ch.hippmann.godot.replication.Synchronizer
import godot.Node
import godot.annotation.RegisterClass

@RegisterClass
class SimpleSynchronizer: Node(), Synchronized by Synchronizer()