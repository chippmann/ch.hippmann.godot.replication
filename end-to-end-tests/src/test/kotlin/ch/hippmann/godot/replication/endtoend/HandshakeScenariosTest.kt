package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(180)
class HandshakeScenariosTest {

    @Test
    fun `a client joins a password protected session and leaves again`() {
        GodotCluster("host_join_password_ok", "host_join_password_ok").use { cluster ->
            val host = cluster.startHost("Mara", "--password=wrench-42")
            val client = cluster.startClient("Tobias", "--password=wrench-42", "--start-delay-milliseconds=500")
            cluster.awaitAllPassed()

            val joined = cluster.assertEvent(client, "joined")
            assertEquals(2, joined.int("master"))
            assertEquals(listOf(2, 3), joined.ints("members"))
            assertEquals(listOf(2, 3), cluster.assertEvent(host, "member_joined").ints("members"))
            val left = cluster.assertEvent(host, "member_left")
            assertEquals(3, left.int("left"))
            assertEquals("GRACEFUL", left.string("reason"))
        }
    }

    @Test
    fun `a wrong password is rejected without side effects`() {
        GodotCluster("join_wrong_password", "join_wrong_password").use { cluster ->
            val host = cluster.startHost("Mara", "--password=wrench-42")
            val client = cluster.startClient("Tobias", "--password=crowbar-7", "--start-delay-milliseconds=500")
            cluster.awaitAllPassed()

            assertEquals("WRONG_PASSWORD", cluster.assertEvent(client, "join_rejected").string("reason"))
            assertEquals("WRONG_PASSWORD", cluster.assertEvent(host, "join_rejected").string("reason"))
        }
    }

    @Test
    fun `godot rpc calls cross the full mesh`() {
        GodotCluster("rpc_over_mesh", "rpc_over_mesh").use { cluster ->
            val host = cluster.startHost("Mara", "--expected-players=3")
            val first = cluster.startClient("Tobias", "--expected-players=3", "--start-delay-milliseconds=500")
            val second = cluster.startClient("Lena", "--expected-players=3", "--start-delay-milliseconds=2500")
            cluster.awaitAllPassed()

            for (process in listOf(host, first, second)) {
                val received = cluster.assertEvent(process, "rpc_received")
                assertEquals(2, received.ints("from").size, "${process.name} received greetings from both others")
                assertTrue(received.player !in received.ints("from"), "${process.name} must not greet itself")
            }
            assertEquals(2, cluster.assertEvent(host, "master_rpc_received").int("count"))
        }
    }
}
