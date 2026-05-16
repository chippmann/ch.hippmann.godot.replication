package ch.hippmann.godot.replication.it

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class SpawnTest : FunSpec({
    val godotBin = System.getProperty("godot.bin")
        ?: error("godot.bin system property not set — configure GODOT_BIN env var")
    val projectDir = File(System.getProperty("godot.project.dir")
        ?: error("godot.project.dir system property not set"))

    test("server-spawned managed scene replicates to two clients") {
        val orch = ProcessOrchestrator(godotBin, projectDir)
        try {
            val scenario = "ch.hippmann.godot.replication.it.scenarios.SpawnScenario"
            val clientCount = 2

            orch.launch(scenario, role = "SERVER", peerId = "server", clientCount = clientCount)
            repeat(clientCount) { i ->
                Thread.sleep(300) // staggered so server is listening
                orch.launch(scenario, role = "CLIENT", peerId = "c${i + 1}")
            }

            val results = orch.awaitAll(timeoutSeconds = 60)

            val failures = results.filterNot { it.passed }
            if (failures.isNotEmpty()) fail(renderFailure(results))

            val clientResults = results.filter { it.role == "CLIENT" }
            clientResults.size shouldBe clientCount
            for (cr in clientResults) {
                val observed = cr.data["observedChildren"]?.jsonPrimitive?.content?.toInt() ?: -1
                if (observed != 1) fail("client ${cr.peerId} saw $observed children, expected 1\n" + renderFailure(results))
            }
        } finally {
            orch.cleanup()
        }
    }
})
