package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.OwnershipPolicy
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.SpawnOptions
import ch.hippmann.godot.replication.sample.world.Crate
import ch.hippmann.godot.replication.sample.world.Door
import godot.api.Node
import godot.api.PackedScene
import godot.core.asStringName
import godot.global.GD
import kotlinx.coroutines.delay

/**
 * The host spawns a transferable crate and a request gated door. Tobias takes the crate by request and
 * pushes it; Lena is refused the door, Tobias gets it; the host hands the crate back explicitly.
 */
class OwnershipTransferScenario : Scenario {
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

        if (Network.isMaster) runHost(runner, world) else runClient(runner, world, context)
    }

    private suspend fun runHost(runner: ScenarioRunner, world: Node) {
        val crateScene = GD.load<PackedScene>("res://scenes/crate.tscn") ?: throw ScenarioFailure("crate scene missing")
        val doorScene = GD.load<PackedScene>("res://scenes/door.tscn") ?: throw ScenarioFailure("door scene missing")
        val crate = Network.spawn<Crate>(crateScene, world, options = SpawnOptions(ownershipPolicy = OwnershipPolicy.Transferable))
        crate.label = "supplies"
        val door = Network.spawn<Door>(doorScene, world, options = SpawnOptions(ownershipPolicy = OwnershipPolicy.RequestRequired))
        door.allowedRequesters = setOf(3)
        ScenarioLog.event("spawned")

        val crateTaken = runner.awaitEvent<NetworkEvent.OwnershipChanged>()
        ScenarioLog.event("crate_taken", "owner" to crateTaken.newOwner.value)
        runner.awaitUntil { crate.pushes == 3 }
        ScenarioLog.event("crate_pushed_by_new_owner", "pushes" to crate.pushes)
        scenarioCheck(!Network.isOwner(crate) && Network.ownerOf(crate) == PlayerId(3)) { "the crate must belong to 3 now" }

        runner.awaitUntil { Network.ownerOf(door) == PlayerId(3) }
        ScenarioLog.event("door_taken", "owner" to Network.ownerOf(door).value)
        runner.awaitUntil { door.open }
        ScenarioLog.event("door_opened_by_owner")

        Network.transferOwnership(crate, PlayerId(4))
        runner.awaitUntil { crate.pushes == 10 }
        ScenarioLog.event("crate_pushed_after_transfer", "owner" to Network.ownerOf(crate).value, "pushes" to crate.pushes)
        runner.awaitConnected(1)
        Network.leave()
    }

    private suspend fun runClient(runner: ScenarioRunner, world: Node, context: ScenarioContext) {
        runner.awaitUntil { world.getChildren().filterIsInstance<Crate>().isNotEmpty() && world.getChildren().filterIsInstance<Door>().isNotEmpty() }
        val crate = world.getChildren().filterIsInstance<Crate>().single()
        val door = world.getChildren().filterIsInstance<Door>().single()
        runner.awaitUntil { crate.label == "supplies" }

        if (Network.localPlayerId == PlayerId(3)) {
            val granted = Network.requestOwnership(crate)
            ScenarioLog.event("crate_request", "granted" to granted)
            scenarioCheck(granted && Network.isOwner(crate)) { "the transferable crate must be granted" }
            crate.pushes = 3
            delay(300)
            val doorGranted = Network.requestOwnership(door)
            ScenarioLog.event("door_request", "granted" to doorGranted)
            scenarioCheck(doorGranted) { "3 is allowed to take the door" }
            door.open = true
            delay(500)
            runner.awaitUntil { Network.ownerOf(crate) == PlayerId(4) }
            runner.awaitUntil { crate.pushes == 10 }
            ScenarioLog.event("saw_final_pushes", "pushes" to crate.pushes)
        } else {
            delay(1_500)
            val doorGranted = Network.requestOwnership(door)
            ScenarioLog.event("door_request", "granted" to doorGranted)
            scenarioCheck(!doorGranted) { "4 must be refused the door" }
            runner.awaitUntil { Network.isOwner(crate) }
            ScenarioLog.event("crate_received", "pushes" to crate.pushes)
            crate.pushes = 10
            delay(1_000)
        }
        delay(300)
        Network.leave()
    }
}
