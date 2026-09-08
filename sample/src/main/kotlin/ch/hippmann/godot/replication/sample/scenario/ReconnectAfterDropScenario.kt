package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Arena
import ch.hippmann.godot.replication.sample.world.Crate
import godot.core.NodePath
import kotlinx.coroutines.delay

/** A client leaves and comes back: it gets a fresh id and the world again. */
class ReconnectAfterDropScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
            Network.loadLevel("res://scenes/arena.tscn", LevelPolicy.StartWhenLoaded)
            runner.awaitUntil { Network.levelNode is Arena }
            val arena = Network.levelNode as Arena
            (arena.getNodeOrNull(NodePath("AmmoCrate")) as Crate).pushes = 9
            repeat(2) { round ->
                val joined = runner.awaitEvent<NetworkEvent.MemberJoined>()
                ScenarioLog.event("member_joined", "round" to round, "member" to joined.member.id.value)
                runner.awaitEvent<NetworkEvent.MemberLeft>()
            }
            runner.awaitConnected(1)
            Network.leave()
            return
        }
        repeat(2) { round ->
            val view = Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            val crate = (Network.levelNode as Arena).getNodeOrNull(NodePath("AmmoCrate")) as Crate
            ScenarioLog.event("joined", "round" to round, "id" to view.localPlayerId.value, "crate_pushes" to crate.pushes)
            scenarioCheck(view.localPlayerId == PlayerId(3 + round)) { "each join must get a fresh id" }
            scenarioCheck(crate.pushes == 9) { "the snapshot must arrive on every join" }
            delay(500)
            Network.leave()
            scenarioCheck(Network.levelNode == null) { "leaving must unload the level" }
            delay(500)
        }
    }
}
