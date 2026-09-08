package ch.hippmann.godot.replication.sample.scenario

import godot.annotation.Rpc
import godot.annotation.RpcMode
import godot.annotation.Script
import godot.api.Node

@Script
class MeshRpcNode : Node() {
    val greetings = LinkedHashMap<Int, String>()
    var masterGreetings = 0

    @Rpc(rpcMode = RpcMode.ANY)
    fun greet(from: Int, text: String) {
        greetings[from] = text
    }

    @Rpc(rpcMode = RpcMode.ANY)
    fun greetMaster(from: Int) {
        masterGreetings++
    }
}
