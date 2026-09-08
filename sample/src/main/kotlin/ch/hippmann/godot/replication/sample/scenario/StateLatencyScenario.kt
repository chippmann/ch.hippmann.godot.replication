package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.LatencyProbe
import godot.api.Node
import godot.api.PackedScene
import godot.core.Vector3
import godot.core.asStringName
import godot.coroutines.awaitNextFrame
import godot.coroutines.awaitNextPhysicsFrame
import godot.global.GD
import kotlinx.coroutines.delay

/** The host stamps a probe every physics frame; the client measures how old each stamp is when it can read it. */
class StateLatencyScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        val world = Node().apply { name = "World".asStringName() }
        runner.getTree()?.root?.addChild(world)
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's lab"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
            runner.awaitConnected(2)
            runHost(runner, world)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
            runner.awaitConnected(2)
            runClient(runner, world)
        }
        Network.leave()
    }

    private suspend fun runHost(runner: ScenarioRunner, world: Node) {
        val scene = GD.load<PackedScene>("res://scenes/latency_probe.tscn") ?: throw ScenarioFailure("probe scene missing")
        val probe = Network.spawn<LatencyProbe>(scene, world)
        val end = System.currentTimeMillis() + STAMPING_MILLISECONDS
        while (System.currentTimeMillis() < end) {
            val now = System.currentTimeMillis()
            probe.reliableStamp = now
            probe.streamStamp = now
            probe.position = Vector3((now - LatencyProbe.hourStart(now)) / 1_000.0 * LatencyProbe.UNITS_PER_SECOND, 0.0, 0.0)
            awaitNextPhysicsFrame()
        }
        ScenarioLog.event("stamping_done")
        runner.awaitConnected(1)
    }

    private suspend fun runClient(runner: ScenarioRunner, world: Node) {
        runner.awaitUntil { world.getChildren().filterIsInstance<LatencyProbe>().isNotEmpty() }
        val probe = world.getChildren().filterIsInstance<LatencyProbe>().single()
        delay(WARM_UP_MILLISECONDS)
        val reliable = ArrayList<Long>()
        val stream = ArrayList<Long>()
        val visual = ArrayList<Long>()
        var lastReliable = 0L
        var lastStream = 0L
        val end = System.currentTimeMillis() + MEASURE_MILLISECONDS
        while (System.currentTimeMillis() < end) {
            val now = System.currentTimeMillis()
            if (probe.reliableStamp != lastReliable) {
                lastReliable = probe.reliableStamp
                reliable += now - lastReliable
            }
            if (probe.streamStamp != lastStream) {
                lastStream = probe.streamStamp
                stream += now - lastStream
            }
            val shownTime = LatencyProbe.hourStart(now) + (probe.position.x / LatencyProbe.UNITS_PER_SECOND * 1_000.0).toLong()
            if (probe.position.x > 0.0) visual += now - shownTime
            awaitNextFrame()
        }
        ScenarioLog.event(
            "latency",
            "reliable_median" to reliable.median(), "reliable_p90" to reliable.percentile(90), "reliable_samples" to reliable.size,
            "stream_median" to stream.median(), "stream_p90" to stream.percentile(90), "stream_samples" to stream.size,
            "visual_median" to visual.median(), "visual_p90" to visual.percentile(90), "visual_samples" to visual.size,
            "interpolation_delay" to Network.configuration.effectiveInterpolationDelayMilliseconds,
        )
        scenarioCheck(reliable.size > 30 && stream.size > 30 && visual.size > 30) { "too few samples: ${reliable.size} ${stream.size} ${visual.size}" }
        delay(300)
    }

    private fun List<Long>.median(): Long = percentile(50)

    private fun List<Long>.percentile(percent: Int): Long {
        if (isEmpty()) return -1
        val sorted = sorted()
        return sorted[((sorted.size - 1) * percent / 100.0).toInt()]
    }

    private companion object {
        const val STAMPING_MILLISECONDS = 5_000L
        const val WARM_UP_MILLISECONDS = 700L
        const val MEASURE_MILLISECONDS = 3_000L
    }
}
