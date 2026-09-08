package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(240)
class ExtrasScenariosTest {

    @Test
    fun `distance based interest withholds state until the viewer comes into range`() {
        GodotCluster("interest_distance", "interest_distance").use { cluster ->
            val host = cluster.startHost("Mara")
            cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--start-delay-milliseconds=2500")
            cluster.awaitAllPassed()

            assertEquals(listOf(100, 100), cluster.assertEvent(lena, "health_before").ints("healths"))
            assertEquals(listOf(44, 45), cluster.assertEvent(lena, "health_after").ints("healths"))
            assertEquals(listOf(45, 100), cluster.assertEvent(host, "health_before").ints("healths"))
            assertEquals(listOf(45, 46), cluster.assertEvent(host, "health_after").ints("healths"))
        }
    }

    @Test
    fun `typed custom messages reach everyone or only the master`() {
        GodotCluster("custom_messages", "custom_messages").use { cluster ->
            val host = cluster.startHost("Mara")
            val tobias = cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            cluster.startClient("Lena", "--start-delay-milliseconds=2500")
            cluster.awaitAllPassed()

            assertEquals(listOf(3, 4), cluster.assertEvent(host, "messages").ints("pings"))
            assertEquals(listOf(200, 400), cluster.assertEvent(tobias, "messages").ints("stamps"))
            assertEquals(emptyList(), cluster.assertEvent(tobias, "messages").ints("pings"))
        }
    }

    @Test
    fun `movement stays smooth under simulated latency and one state packet per tick is sent`() {
        GodotCluster("simulated_latency", "simulated_latency").use { cluster ->
            val host = cluster.startHost("Mara")
            val client = cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            cluster.awaitAllPassed()

            val statistics = cluster.assertEvent(host, "statistics")
            val packetsPerSecond = statistics.int("state_packets_per_second")!!
            assertTrue(packetsPerSecond in 25..36, "one state packet per tick, saw $packetsPerSecond per second")
            val smoothness = cluster.assertEvent(client, "smoothness")
            assertTrue(smoothness.fields["largest_jump"].toString().toDouble() < 0.5)
        }
    }
}
