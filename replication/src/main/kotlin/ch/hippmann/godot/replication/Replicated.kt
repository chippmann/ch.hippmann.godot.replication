package ch.hippmann.godot.replication

import godot.api.Node
import godot.api.PackedScene
import godot.annotation.Export
import godot.annotation.RegisterFunction
import godot.annotation.RegisterProperty
import godot.annotation.Rpc
import godot.annotation.RpcMode
import godot.core.StringName
import godot.core.VariantArray

interface Replicated : WithRemoteListeners {
    @Export
    @RegisterProperty
    var managedScenes: VariantArray<PackedScene>

    fun <T> T.initReplication() where T : Node, T : Replicated

    @RegisterFunction
    fun notificationOnChildEnteredTreeForReplicated(child: Node)

    @RegisterFunction
    fun notificationOnChildExitingTreeForReplicated(child: Node)

    // Spawn / despawn flows from server (authority) outward to peers. RpcMode.AUTHORITY
    // means godot-kotlin-jvm will reject calls from any peer other than the node's
    // multiplayer authority. Explicit to insulate against future plugin default changes.
    @Rpc(rpcMode = RpcMode.AUTHORITY)
    @RegisterFunction
    fun peerSpawnForReplicated(spawnNodeData: String)

    @Rpc(rpcMode = RpcMode.AUTHORITY)
    @RegisterFunction
    fun peerSpawnAllForReplicated(spawnNodesData: String)

    @Rpc(rpcMode = RpcMode.AUTHORITY)
    @RegisterFunction
    fun peerDespawnForReplicated(name: StringName)
}
