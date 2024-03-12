package ch.hippmann.godot.replication

import godot.Node
import godot.PackedScene
import godot.annotation.Export
import godot.annotation.RegisterFunction
import godot.annotation.RegisterProperty
import godot.annotation.Rpc
import godot.core.StringName
import godot.core.VariantArray

interface Replicated: WithRemoteListeners {
    @Export
    @RegisterProperty
    var managedScenes: VariantArray<PackedScene>

    fun <T> T.initReplication() where T : Node, T: Replicated

    @RegisterFunction
    fun notificationOnChildEnteredTreeForReplicated(child: Node)
    @RegisterFunction
    fun notificationOnChildExitingTreeForReplicated(child: Node)

    @Rpc
    @RegisterFunction
    fun peerSpawnForReplicated(spawnNodeData: String)

    @Rpc
    @RegisterFunction
    fun peerSpawnAllForReplicated(spawnNodesData: String)

    @Rpc
    @RegisterFunction
    fun peerDespawnForReplicated(name: StringName)
}
