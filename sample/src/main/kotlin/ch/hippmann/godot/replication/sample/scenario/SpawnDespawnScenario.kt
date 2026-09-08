package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Projectile
import godot.api.Node
import godot.api.PackedScene
import godot.core.Vector3
import godot.core.asStringName
import godot.global.GD
import kotlinx.coroutines.delay

/** The host spawns a burst of projectiles and despawns them again; every member sees both. */
class SpawnDespawnScenario : Scenario {
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

        if (Network.isMaster) {
            val scene = GD.load<PackedScene>("res://scenes/projectile.tscn") ?: throw ScenarioFailure("projectile scene missing")
            val projectiles = (1..PROJECTILES).map { index ->
                Network.spawn<Projectile>(scene, world).apply {
                    velocity = Vector3(index.toDouble(), 0.0, 0.0)
                    shooter = Network.localPlayerId.value
                }
            }
            ScenarioLog.event("spawned", "count" to projectiles.size)
            delay(1_500)
            projectiles.forEach(Network::despawn)
            ScenarioLog.event("despawned")
            runner.awaitConnected(1)
        } else {
            runner.awaitUntil { projectiles(world).size == PROJECTILES }
            delay(200)
            val velocities = projectiles(world).map { projectile -> projectile.velocity.x }.sorted()
            val shooters = projectiles(world).map { projectile -> projectile.shooter }.toSet()
            ScenarioLog.event("projectiles_visible", "count" to projectiles(world).size, "velocities" to velocities, "shooters" to shooters.toList())
            scenarioCheck(velocities == (1..PROJECTILES).map { it.toDouble() }) { "unexpected velocities $velocities" }
            scenarioCheck(shooters == setOf(2)) { "unexpected shooters $shooters" }
            runner.awaitUntil { projectiles(world).isEmpty() }
            ScenarioLog.event("projectiles_gone")
        }
        Network.leave()
    }

    private fun projectiles(world: Node): List<Projectile> = world.getChildren().filterIsInstance<Projectile>()

    private companion object {
        const val PROJECTILES = 5
    }
}
