package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(240)
class LevelScenariosTest {

    @Test
    fun `wait for all holds the level until the slowest member loaded`() {
        GodotCluster("level_wait_for_all", "level_wait_for_all").use { cluster ->
            val host = cluster.startHost("Mara")
            val tobias = cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--start-delay-milliseconds=2500", "--level-preparation-milliseconds=3000")
            cluster.awaitAllPassed()

            for (process in listOf(host, tobias, lena)) {
                val loaded = cluster.assertEvent(process, "level_loaded")
                assertEquals("false", loaded.string("started"), "${process.name} must not start on load")
                assertEquals(0, loaded.int("frames"), "${process.name} must not process before the start")
                assertTrue(cluster.assertEvent(process, "level_started").int("frames")!! > 0)
            }
            assertEquals(listOf(2, 3, 4), cluster.assertEvent(host, "level_started").ints("loaded"))
            assertEquals("false", cluster.assertEvent(tobias, "still_waiting").string("started"))
            assertEquals(0, cluster.assertEvent(tobias, "still_waiting").int("frames"))
        }
    }

    @Test
    fun `start when loaded lets every member run as soon as it loaded`() {
        GodotCluster("level_start_when_loaded", "level_start_when_loaded").use { cluster ->
            val host = cluster.startHost("Mara")
            val tobias = cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            val lena = cluster.startClient("Lena", "--start-delay-milliseconds=2500", "--level-preparation-milliseconds=3000")
            cluster.awaitAllPassed()

            for (process in listOf(host, tobias, lena)) {
                assertEquals("true", cluster.assertEvent(process, "level_loaded").string("started"), "${process.name} starts on load")
                assertTrue(cluster.assertEvent(process, "level_started").int("frames")!! > 0)
            }
        }
    }

    @Test
    fun `a late joiner receives the level, the spawned players and the scene placed state`() {
        GodotCluster("late_join_snapshot", "late_join_snapshot").use { cluster ->
            val host = cluster.startHost("Mara")
            cluster.startClient("Tobias", "--start-delay-milliseconds=500")
            host.awaitEvent("world_built", 60_000)
            val lena = cluster.startClient("Lena", "--behavior=late", "--start-delay-milliseconds=1500")
            cluster.awaitAllPassed()

            val snapshot = cluster.assertEvent(lena, "snapshot_seen")
            assertEquals(listOf(52, 53), snapshot.ints("healths"))
            assertEquals(listOf(2, 3), snapshot.ints("credits"))
            assertEquals(5, snapshot.int("crate_pushes"))
            assertEquals("pushed by master", snapshot.string("crate_label"))
            assertEquals(2, snapshot.int("crate_owner"))
        }
    }

    @Test
    fun `a client can leave and rejoin with a fresh id and a fresh snapshot`() {
        GodotCluster("reconnect_after_drop", "reconnect_after_drop").use { cluster ->
            val host = cluster.startHost("Mara")
            val client = cluster.startClient("Tobias", "--start-delay-milliseconds=1000")
            cluster.awaitAllPassed()

            val joins = cluster.eventsOf(client, "joined")
            assertEquals(listOf(3, 4), joins.map { event -> event.int("id") })
            assertEquals(listOf(9, 9), joins.map { event -> event.int("crate_pushes") })
            assertEquals(2, cluster.eventsOf(host, "member_joined").size)
        }
    }
}
