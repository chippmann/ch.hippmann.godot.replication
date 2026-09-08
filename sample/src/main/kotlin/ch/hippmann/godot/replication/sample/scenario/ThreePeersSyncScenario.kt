package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Loadout
import ch.hippmann.godot.replication.sample.world.Player
import godot.api.Node
import godot.api.PackedScene
import godot.core.Vector3
import godot.core.asStringName
import godot.global.GD
import kotlinx.coroutines.delay

/** Every member spawns its own avatar; the others must see its data, its health change and its movement. */
class ThreePeersSyncScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        val world = Node().apply { name = "World".asStringName() }
        runner.getTree()?.root?.addChild(world)
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(context.expectedPlayers)

        val scene = GD.load<PackedScene>("res://scenes/player.tscn") ?: throw ScenarioFailure("player scene missing")
        val local = Network.spawn<Player>(scene, world, spawnData = Loadout("wrench", credits = 10 * Network.localPlayerId.value))
        local.displayName = context.playerName
        local.position = Vector3(Network.localPlayerId.value * 10.0, 0.0, 0.0)
        local.speed = 2.0
        ScenarioLog.event("spawned", "name" to local.name.toString())

        runner.awaitUntil { players(world).size == context.expectedPlayers }
        ScenarioLog.event("players_visible", "names" to players(world).map { player -> player.name.toString() }.sorted())
        delay(1_000)
        local.health = 42

        val remotes = players(world).filter { player -> !Network.isOwner(player) }
        runner.awaitUntil { remotes.all { player -> player.health == 42 && player.displayName.isNotEmpty() } }
        val startPositions = remotes.associate { player -> player.name.toString() to player.position.x }
        delay(1_000)
        val moved = remotes.filter { player -> player.position.x > startPositions.getValue(player.name.toString()) + 0.5 }
        scenarioCheck(moved.size == remotes.size) { "remote players did not move: ${remotes.map { it.name.toString() to it.position.x }}" }
        for (player in remotes) {
            val owner = Network.ownerOf(player)
            scenarioCheck(player.loadout == Loadout("wrench", 10 * owner.value)) { "spawn data mismatch for ${player.name}: ${player.loadout}" }
            scenarioCheck(player.displayName.isNotEmpty()) { "display name missing for ${player.name}" }
        }
        ScenarioLog.event(
            "sync_verified",
            "remotes" to remotes.map { player -> player.displayName }.sorted(),
            "healths" to remotes.map { player -> player.health },
            "credits" to remotes.map { player -> player.loadout.credits }.sorted(),
        )
        if (Network.isMaster) {
            runner.awaitConnected(1)
            runner.awaitUntil { players(world).size == 1 }
            ScenarioLog.event("remotes_despawned", "remaining" to players(world).size)
        } else {
            delay(500)
        }
        Network.leave()
    }

    private fun players(world: Node): List<Player> = world.getChildren().filterIsInstance<Player>()
}
