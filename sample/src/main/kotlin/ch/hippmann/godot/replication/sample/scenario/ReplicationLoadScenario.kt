package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.diagnostics.NetworkStatistics
import ch.hippmann.godot.replication.sample.world.Drone
import ch.hippmann.godot.replication.sample.world.Orbit
import godot.api.Node
import godot.api.PackedScene
import godot.core.asStringName
import godot.coroutines.awaitNextFrame
import godot.global.GD
import kotlinx.coroutines.delay

/** The host owns many moving nodes; every member reports what that costs it per second and that all of them move. */
class ReplicationLoadScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        val world = Node().apply { name = "World".asStringName() }
        runner.getTree()?.root?.addChild(world)
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's swarm"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(context.expectedPlayers)
        if (Network.isMaster) spawnDrones(world)
        runner.awaitUntil { drones(world).size == DRONES }
        ScenarioLog.event("drones_visible")

        delay(WARM_UP_MILLISECONDS)
        val positions = drones(world).associateWith { drone -> drone.position }
        val windows = ArrayList<NetworkStatistics>()
        var last = Network.statistics.value
        val end = System.currentTimeMillis() + MEASURE_MILLISECONDS
        while (System.currentTimeMillis() < end) {
            val current = Network.statistics.value
            if (current !== last) {
                last = current
                windows += current
            }
            awaitNextFrame()
        }
        val moving = drones(world).count { drone -> drone.position.distanceTo(positions.getValue(drone)) > 0.5 }
        ScenarioLog.event(
            "load",
            "drones" to DRONES,
            "moving" to moving,
            "windows" to windows.size,
            "processing_ms" to windows.map { it.processingMilliseconds }.average(),
            "packets_in" to windows.map { it.packetsIn }.average(),
            "bytes_in" to windows.map { it.bytesIn }.average(),
            "packets_out" to windows.map { it.packetsOut }.average(),
            "bytes_out" to windows.map { it.bytesOut }.average(),
        )
        scenarioCheck(windows.size >= 2) { "statistics never advanced" }
        if (Network.isMaster) {
            runner.awaitConnected(1)
        } else {
            delay(500)
        }
        Network.leave()
    }

    private fun spawnDrones(world: Node) {
        val scene = GD.load<PackedScene>("res://scenes/drone.tscn") ?: throw ScenarioFailure("drone scene missing")
        repeat(DRONES) { index ->
            val orbit = Orbit(centerX = (index % 15) * 8.0, centerZ = (index / 15) * 8.0, phase = index * 0.4)
            Network.spawn<Drone>(scene, world, spawnData = orbit)
        }
    }

    private fun drones(world: Node): List<Drone> = world.getChildren().filterIsInstance<Drone>()

    private companion object {
        const val DRONES = 150
        const val WARM_UP_MILLISECONDS = 1_500L
        const val MEASURE_MILLISECONDS = 3_200L
    }
}
