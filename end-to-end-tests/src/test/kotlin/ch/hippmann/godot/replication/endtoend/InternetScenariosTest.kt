package ch.hippmann.godot.replication.endtoend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Joining by code through the rendezvous service; on one machine every path ends on localhost, but each strategy runs for real. */
@Timeout(300)
class InternetScenariosTest {

    @Test
    fun `members join by code and reach each other directly`() = joinByCode("join_by_code_direct", emptyList(), expectedStrategy = "direct")

    @Test
    fun `members join by code through hole punching`() =
        joinByCode("join_by_code_punch", listOf("--strategies=punchthrough,relay", "--punch-private=true"), expectedStrategy = "punchthrough")

    @Test
    fun `members join by code through the relay`() = joinByCode("join_by_code_relay", listOf("--strategies=relay"), expectedStrategy = "relay")

    private fun joinByCode(testName: String, extra: List<String>, expectedStrategy: String) {
        RendezvousServer(File(SampleProject.logsDirectory, testName)).use { service ->
            GodotCluster(testName, "join_by_code").use { cluster ->
                val arguments = listOf("--rendezvous=${service.url}", "--expected-players=3") + extra
                val host = cluster.startHost("Mara", *arguments.toTypedArray())
                val code = assertNotNull(cluster.assertEvent(host, "hosting").string("code"), "the host got no session code")
                assertEquals(6, code.length)
                val tobias = cluster.startClient("Tobias", *(arguments + "--code=$code").toTypedArray())
                val lena = cluster.startClient("Lena", *(arguments + listOf("--code=$code", "--start-delay-milliseconds=3000")).toTypedArray())
                cluster.awaitAllPassed()

                for (process in listOf(tobias, lena)) {
                    assertEquals(expectedStrategy, cluster.assertEvent(process, "joined").string("master_strategy"), "${process.name} reached the master")
                }
                val lenaMesh = cluster.assertEvent(lena, "meshed").string("strategies").orEmpty()
                assertTrue(lenaMesh.contains("3=$expectedStrategy"), "Lena's link to Tobias: $lenaMesh")
                assertEquals(listOf(43, 44), cluster.assertEvent(host, "state_seen").ints("healths"))
                assertEquals(listOf(42, 43), cluster.assertEvent(lena, "state_seen").ints("healths"))
            }
        }
    }
}
