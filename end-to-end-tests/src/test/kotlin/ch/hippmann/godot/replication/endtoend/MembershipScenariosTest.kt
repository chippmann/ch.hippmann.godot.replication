package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(240)
class MembershipScenariosTest {

    @Test
    fun `graceful and abrupt leaves are observed with their reasons`() {
        GodotCluster("client_leave", "client_leave").use { cluster ->
            val host = cluster.startHost("Mara", "--expected-players=3", "--behavior=observe")
            cluster.startClient("Tobias", "--expected-players=3", "--behavior=leave", "--start-delay-milliseconds=500")
            cluster.startClient("Lena", "--expected-players=3", "--behavior=crash", "--start-delay-milliseconds=2500", abruptExit = true)
            cluster.awaitAllPassed()

            val leaves = cluster.eventsOf(host, "member_left").associate { event -> event.int("left") to event.string("reason") }
            assertEquals(mapOf<Int?, String?>(3 to "GRACEFUL", 4 to "TIMEOUT"), leaves)
        }
    }

    @Test
    fun `the lowest remaining id becomes master and a late joiner is redirected to it`() {
        GodotCluster("master_leave_reelection", "master_leave_reelection").use { cluster ->
            val host = cluster.startHost("Mara", "--expected-players=3", "--password=wrench-42")
            val tobias = cluster.startClient("Tobias", "--expected-players=3", "--password=wrench-42", "--start-delay-milliseconds=500")
            val lenaPort = GodotCluster.freeUdpPort()
            val lena = cluster.startClient("Lena", "--expected-players=3", "--password=wrench-42", "--start-delay-milliseconds=2500", "--port=$lenaPort")
            host.awaitEvent("host_left", 60_000)
            tobias.awaitEvent("master_changed", 30_000)
            val late = cluster.startClient("Noah", "--behavior=late", "--password=wrench-42", "--join=127.0.0.1:$lenaPort")
            cluster.awaitAllPassed()

            for (member in listOf(tobias, lena)) {
                val changed = cluster.assertEvent(member, "master_changed")
                assertEquals(3, changed.int("master"), "${member.name} sees the new master")
                assertEquals(2, changed.int("epoch"), "${member.name} sees epoch 2")
                assertEquals(5, cluster.assertEvent(member, "late_member_joined").int("member"))
                assertEquals(listOf(3, 4, 5), cluster.assertEvent(member, "late_mesh_complete").ints("connected"))
            }
            assertEquals(true, cluster.assertEvent(tobias, "master_changed").fields["is_master"].toString().toBoolean())
            val joined = cluster.assertEvent(late, "joined")
            assertEquals(3, joined.int("master"))
            assertEquals(listOf(3, 4, 5), joined.ints("members"))
        }
    }

    @Test
    fun `hosts are discovered on the local network`() {
        GodotCluster("lan_discovery", "lan_discovery").use { cluster ->
            val discoveryPort = GodotCluster.freeUdpPort()
            val host = cluster.startHost("Mara", "--password=wrench-42", "--discovery-port=$discoveryPort")
            val client = cluster.startClient("Tobias", "--password=wrench-42", "--discovery-port=$discoveryPort", "--start-delay-milliseconds=500")
            cluster.awaitAllPassed()

            assertTrue(cluster.assertEvent(client, "discovered").int("count")!! >= 1)
            assertEquals(cluster.hostPort, cluster.assertEvent(client, "joined_discovered").int("port"))
            assertEquals(3, cluster.assertEvent(host, "member_left").int("left"))
        }
    }

    @Test
    fun `ready flags, profiles, lobby changes and kicks replicate`() {
        GodotCluster("lobby_flow", "lobby_flow").use { cluster ->
            val host = cluster.startHost("Mara")
            val client = cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            cluster.awaitAllPassed()

            assertEquals("Tobias the Second", cluster.assertEvent(host, "client_renamed").string("name"))
            assertEquals("capture", cluster.assertEvent(host, "client_requested_mode").string("mode"))
            assertEquals("3", cluster.assertEvent(client, "lobby_renamed").string("rounds"))
            assertEquals("KICKED", cluster.assertEvent(host, "member_left").string("reason"))
            assertEquals("KICKED", cluster.assertEvent(client, "kicked").string("reason"))
        }
    }
}
