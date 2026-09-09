package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Loadout
import ch.hippmann.godot.replication.sample.world.Player
import godot.api.Node
import godot.api.PackedScene
import godot.core.asStringName
import godot.global.GD
import kotlinx.coroutines.delay

/** The host registers with the rendezvous service; the others join by its code and must reach every member somehow. */
class JoinByCodeScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        val world = Node().apply { name = "World".asStringName() }
        runner.getTree()?.root?.addChild(world)
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's online arena"), PlayerProfile(context.playerName), context.port, online = true)
            ScenarioLog.event("hosting", "port" to context.port, "code" to Network.sessionCode)
        } else {
            val code = context.arguments["code"] ?: throw ScenarioFailure("--code is missing")
            Network.joinByCode(code, PlayerProfile(context.playerName))
            val master = Network.master
            ScenarioLog.event("joined", "code" to code, "master_strategy" to Network.connectionStrategyOf(master))
        }
        runner.awaitConnected(context.expectedPlayers)
        val strategies = Network.session.value?.connected.orEmpty().filter { id -> id != Network.localPlayerId }
            .associate { id -> id.value.toString() to Network.connectionStrategyOf(id) }
        ScenarioLog.event("meshed", "strategies" to strategies.entries.joinToString(",") { (id, strategy) -> "$id=$strategy" }, "encrypted" to Network.isEncrypted)

        val scene = GD.load<PackedScene>("res://scenes/player.tscn") ?: throw ScenarioFailure("player scene missing")
        val local = Network.spawn<Player>(scene, world, spawnData = Loadout("wrench", Network.localPlayerId.value))
        local.displayName = context.playerName
        runner.awaitUntil { world.getChildren().filterIsInstance<Player>().size == context.expectedPlayers }
        delay(500)
        local.health = 40 + Network.localPlayerId.value
        val remotes = world.getChildren().filterIsInstance<Player>().filter { player -> !Network.isOwner(player) }
        runner.awaitUntil { remotes.all { player -> player.health != 100 } }
        ScenarioLog.event("state_seen", "healths" to remotes.map { player -> player.health }.sorted())
        if (Network.isMaster) runner.awaitConnected(1) else delay(500)
        Network.leave()
    }
}
