package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.OwnerLeavePolicy
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.SpawnOptions
import ch.hippmann.godot.replication.sample.world.Crate
import ch.hippmann.godot.replication.sample.world.Projectile
import godot.api.Node
import godot.api.PackedScene
import godot.core.asStringName
import godot.global.GD
import kotlinx.coroutines.delay

/**
 * Everyone spawns a crate (transfer to master) and a projectile (despawn). Tobias leaves, then the host leaves;
 * Lena ends up owning every crate and no projectile but her own.
 */
class OwnerLeavePoliciesScenario : Scenario {
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
        runner.awaitConnected(3)

        val crateScene = GD.load<PackedScene>("res://scenes/crate.tscn") ?: throw ScenarioFailure("crate scene missing")
        val projectileScene = GD.load<PackedScene>("res://scenes/projectile.tscn") ?: throw ScenarioFailure("projectile scene missing")
        val crate = Network.spawn<Crate>(crateScene, world, options = SpawnOptions(ownerLeavePolicy = OwnerLeavePolicy.TransferToMaster))
        crate.label = "crate of ${context.playerName}"
        Network.spawn<Projectile>(projectileScene, world, options = SpawnOptions(ownerLeavePolicy = OwnerLeavePolicy.Despawn))
        runner.awaitUntil { crates(world).size == 3 && projectiles(world).size == 3 }
        ScenarioLog.event("world_complete")

        when (Network.localPlayerId) {
            PlayerId(3) -> {
                delay(500)
                Network.leave()
            }
            PlayerId(2) -> {
                val left = runner.awaitEvent<NetworkEvent.MemberLeft>()
                runner.awaitUntil { projectiles(world).size == 2 && crates(world).all { Network.ownerOf(it) != left.player } }
                val inherited = crates(world).single { it.label == "crate of Tobias" }
                scenarioCheck(Network.isOwner(inherited)) { "the master must inherit Tobias' crate" }
                inherited.pushes = 7
                ScenarioLog.event("inherited_crate", "owner" to Network.ownerOf(inherited).value)
                delay(1_000)
                Network.leave()
            }
            else -> {
                runner.awaitUntil { projectiles(world).size == 2 }
                val tobiasCrate = crates(world).single { it.label == "crate of Tobias" }
                runner.awaitUntil { Network.ownerOf(tobiasCrate) == PlayerId(2) && tobiasCrate.pushes == 7 }
                ScenarioLog.event("saw_master_inherit", "pushes" to tobiasCrate.pushes)
                runner.awaitEvent<NetworkEvent.MasterChanged>()
                runner.awaitUntil { projectiles(world).size == 1 && crates(world).size == 3 && crates(world).all { Network.isOwner(it) } }
                ScenarioLog.event("inherited_all", "crates" to crates(world).map { it.label }.sorted(), "projectiles" to projectiles(world).size)
                Network.leave()
            }
        }
    }

    private fun crates(world: Node): List<Crate> = world.getChildren().filterIsInstance<Crate>()

    private fun projectiles(world: Node): List<Projectile> = world.getChildren().filterIsInstance<Projectile>()
}
