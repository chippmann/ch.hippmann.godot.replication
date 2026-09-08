package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals

@Timeout(240)
class ReplicationScenariosTest {

    @Test
    fun `owned properties, spawn data and movement replicate to every peer`() {
        GodotCluster("three_peers_sync", "three_peers_sync").use { cluster ->
            val host = cluster.startHost("Mara", "--expected-players=3")
            val tobias = cluster.startClient("Tobias", "--expected-players=3", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--expected-players=3", "--start-delay-milliseconds=2500")
            cluster.awaitAllPassed()

            assertEquals(listOf("Lena", "Tobias"), cluster.assertEvent(host, "sync_verified").fields["remotes"].toString().removeSurrounding("[", "]").split(",").map { it.trim('"') })
            assertEquals(listOf(30, 40), cluster.assertEvent(host, "sync_verified").ints("credits"))
            assertEquals(listOf(20, 40), cluster.assertEvent(tobias, "sync_verified").ints("credits"))
            assertEquals(listOf(20, 30), cluster.assertEvent(lena, "sync_verified").ints("credits"))
            assertEquals(1, cluster.assertEvent(host, "remotes_despawned").int("remaining"))
        }
    }

    @Test
    fun `spawned nodes appear and disappear on every peer`() {
        GodotCluster("spawn_despawn", "spawn_despawn").use { cluster ->
            val host = cluster.startHost("Mara", "--expected-players=3")
            val tobias = cluster.startClient("Tobias", "--expected-players=3", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--expected-players=3", "--start-delay-milliseconds=2500")
            cluster.awaitAllPassed()

            assertEquals(5, cluster.assertEvent(host, "spawned").int("count"))
            for (client in listOf(tobias, lena)) {
                assertEquals(5, cluster.assertEvent(client, "projectiles_visible").int("count"))
                cluster.assertEvent(client, "projectiles_gone")
            }
        }
    }
}
