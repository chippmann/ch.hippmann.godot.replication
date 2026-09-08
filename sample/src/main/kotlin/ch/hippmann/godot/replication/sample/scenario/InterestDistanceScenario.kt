package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Loadout
import ch.hippmann.godot.replication.sample.world.Player
import godot.api.Node
import godot.api.PackedScene
import godot.core.Vector3
import godot.core.asStringName
import godot.global.GD
import kotlinx.coroutines.delay

/** Lena starts far away and must not receive the others' state until she walks into range. */
class InterestDistanceScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        Player.interestRange = 50.0
        val world = Node().apply { name = "World".asStringName() }
        runner.getTree()?.root?.addChild(world)
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(3)

        val scene = GD.load<PackedScene>("res://scenes/player.tscn") ?: throw ScenarioFailure("player scene missing")
        val startX = if (Network.localPlayerId == PlayerId(4)) 1_000.0 else Network.localPlayerId.value * 10.0
        val local = Network.spawn<Player>(scene, world, spawnData = Loadout("wrench", 0, startX))
        local.displayName = context.playerName
        runner.awaitUntil { players(world).size == 3 }
        delay(500)
        local.health = 42 + Network.localPlayerId.value
        delay(1_500)

        val remotes = players(world).filter { player -> !Network.isOwner(player) }.sortedBy { Network.ownerOf(it).value }
        ScenarioLog.event("health_before", "healths" to remotes.map { player -> player.health }, "names" to remotes.map { player -> player.displayName })
        if (Network.localPlayerId == PlayerId(4)) {
            scenarioCheck(remotes.all { player -> player.health == 100 }) { "the far player must not receive state, saw ${remotes.map { it.health }}" }
            // The near players flag ready once they verified that nothing of Lena arrived; only then does she walk into range.
            runner.awaitUntil { Network.lobby.value.players.count { player -> player.ready } == 2 }
            local.position = Vector3(5.0, 0.0, 0.0)
            runner.awaitUntil { remotes.all { player -> player.health != 100 } }
            ScenarioLog.event("health_after", "healths" to remotes.map { player -> player.health }, "names" to remotes.map { player -> player.displayName })
        } else {
            scenarioCheck(remotes.single { Network.ownerOf(it) != PlayerId(4) }.health != 100) { "near players must exchange state" }
            scenarioCheck(remotes.single { Network.ownerOf(it) == PlayerId(4) }.health == 100) { "the far player's state must not arrive" }
            Network.setReady(true)
            runner.awaitUntil { remotes.all { player -> player.health != 100 } }
            ScenarioLog.event("health_after", "healths" to remotes.map { player -> player.health })
        }
        if (Network.isMaster) runner.awaitConnected(1) else delay(500)
        Network.leave()
    }

    private fun players(world: Node): List<Player> = world.getChildren().filterIsInstance<Player>()
}
