package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import godot.core.asStringName

class RpcOverMeshScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        val rpcNode = MeshRpcNode().apply { name = "MeshRpc".asStringName() }
        runner.getTree()?.root?.addChild(rpcNode)

        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        val view = runner.awaitConnected(context.expectedPlayers)
        ScenarioLog.event("mesh_complete", "connected" to view.connected.map { id -> id.value }.sorted())

        val others = view.connected - Network.localPlayerId
        rpcNode.rpc(rpcNode::greet, Network.localPlayerId.value, "hello from ${context.playerName}")
        if (!Network.isMaster) rpcNode.rpcId(Network.master.value.toLong(), rpcNode::greetMaster, Network.localPlayerId.value)

        runner.awaitUntil { rpcNode.greetings.keys.containsAll(others.map { id -> id.value }) }
        ScenarioLog.event("rpc_received", "from" to rpcNode.greetings.keys.sorted())
        if (Network.isMaster) {
            runner.awaitUntil { rpcNode.masterGreetings == others.size }
            ScenarioLog.event("master_rpc_received", "count" to rpcNode.masterGreetings)
            runner.awaitConnected(1)
        }
        Network.leave()
    }
}
