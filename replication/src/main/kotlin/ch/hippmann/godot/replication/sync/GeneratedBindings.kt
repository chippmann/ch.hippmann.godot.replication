package ch.hippmann.godot.replication.sync

import godot.api.Node

/** Finds the generated property sets of a node's class and its ancestors, base class first, once per class. */
internal object GeneratedBindings {
    private val byClass = HashMap<Class<*>, List<GeneratedSyncedProperties<Node>>>()

    fun forNode(node: Node): List<GeneratedSyncedProperties<Node>> = byClass.getOrPut(node.javaClass) { load(node.javaClass) }

    private fun load(type: Class<*>): List<GeneratedSyncedProperties<Node>> =
        generateSequence(type) { current -> current.superclass }.mapNotNull(::generatedFor).toList().asReversed()

    @Suppress("UNCHECKED_CAST")
    private fun generatedFor(type: Class<*>): GeneratedSyncedProperties<Node>? = try {
        Class.forName(type.name + SUFFIX, true, type.classLoader).getField("INSTANCE").get(null) as GeneratedSyncedProperties<Node>
    } catch (missing: ClassNotFoundException) {
        null
    }

    const val SUFFIX = "SyncedProperties"
}
