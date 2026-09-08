package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertTrue

/** Bounds are loose enough for a shared CI runner; the measured numbers are printed for the humans reading the log. */
@Timeout(240)
class PerformanceScenariosTest {

    @Test
    fun `state changes reach the other peer within a few ticks`() {
        GodotCluster("state_latency", "state_latency").use { cluster ->
            cluster.startHost("Mara")
            val tobias = cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            cluster.awaitAllPassed()

            val latency = cluster.assertEvent(tobias, "latency")
            println("state latency in milliseconds: ${latency.fields}")
            assertUnder(latency, "reliable_median", 70.0)
            assertUnder(latency, "stream_median", 70.0)
            assertUnder(latency, "visual_median", 160.0)
            assertUnder(latency, "reliable_p90", 120.0)
            assertUnder(latency, "stream_p90", 120.0)
            assertUnder(latency, "visual_p90", 220.0)
        }
    }

    @Test
    fun `150 moving nodes stay cheap for every member`() {
        GodotCluster("replication_load", "replication_load").use { cluster ->
            val host = cluster.startHost("Mara", "--expected-players=3")
            val tobias = cluster.startClient("Tobias", "--expected-players=3", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--expected-players=3", "--start-delay-milliseconds=1500")
            cluster.awaitAllPassed()

            for (process in listOf(host, tobias, lena)) {
                val load = cluster.assertEvent(process, "load")
                println("load on ${process.name}: ${load.fields}")
                assertTrue((load.int("moving") ?: 0) >= 140, "${process.name} saw only ${load.int("moving")} moving drones")
                assertUnder(load, "processing_ms", if (process === host) 600.0 else 400.0)
                if (process !== host) {
                    val packetsIn = load.double("packets_in") ?: 0.0
                    assertTrue(packetsIn >= 80.0, "${process.name} received only $packetsIn state packets per second, ENet is throttling")
                }
            }
        }
    }

    private fun assertUnder(event: ScenarioEvent, key: String, bound: Double) {
        val value = event.double(key) ?: error("$key missing in ${event.fields}")
        assertTrue(value <= bound, "$key was $value, expected at most $bound")
    }
}
