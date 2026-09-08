package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkSimulation
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Loadout
import ch.hippmann.godot.replication.sample.world.Player
import godot.api.Node
import godot.api.PackedScene
import godot.core.asStringName
import godot.coroutines.awaitNextFrame
import godot.global.GD
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Under 100 ms latency with jitter and loss the remote avatar still moves every frame without jumps. */
class SimulatedLatencyScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        Network.simulation = NetworkSimulation(latencyMilliseconds = 100, jitterMilliseconds = 20, lossPercent = 5.0)
        val world = Node().apply { name = "World".asStringName() }
        runner.getTree()?.root?.addChild(world)
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(2)

        if (Network.isMaster) {
            val scene = GD.load<PackedScene>("res://scenes/player.tscn") ?: throw ScenarioFailure("player scene missing")
            val player = Network.spawn<Player>(scene, world, spawnData = Loadout("wrench", 1))
            player.speed = 2.0
            delay(2_500)
            val statistics = Network.statistics.value
            ScenarioLog.event("statistics", "state_packets_per_second" to statistics.statePacketsOut, "ticks" to statistics.ticks, "bytes_out" to statistics.bytesOut, "packets_out" to statistics.packetsOut, "round_trip" to statistics.roundTripMilliseconds.values.firstOrNull(), "all" to statistics.toString())
            runner.awaitConnected(1)
        } else {
            runner.awaitUntil { world.getChildren().filterIsInstance<Player>().isNotEmpty() }
            val remote = world.getChildren().filterIsInstance<Player>().single()
            delay(500)
            var previous = remote.position.x
            var largestJump = 0.0
            var stillFrames = 0
            var frames = 0
            val start = previous
            while (frames < 180) {
                awaitNextFrame()
                val current = remote.position.x
                val jump = abs(current - previous)
                if (jump > largestJump) largestJump = jump
                if (jump == 0.0) stillFrames++
                previous = current
                frames++
            }
            ScenarioLog.event("smoothness", "moved" to previous - start, "largest_jump" to largestJump, "still_frames" to stillFrames, "dropped" to Network.statistics.value.droppedEntries)
            scenarioCheck(previous - start > 1.5) { "the remote avatar barely moved: ${previous - start}" }
            scenarioCheck(largestJump < 0.5) { "movement jumped by $largestJump" }
            scenarioCheck(stillFrames < 60) { "movement froze for $stillFrames of $frames frames" }
        }
        Network.leave()
    }
}
