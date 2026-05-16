package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.SpawnScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class FourClientSpawnTest : FunSpec({
    test("server-spawned managed scene replicates to four clients (peer-count generalization)") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 4
            orchestrator.launch(
                scenarioClass = SpawnScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { clientIndex ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = SpawnScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${clientIndex + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 90)
            results.assertAllPassed()

            val clientResults = results.clientsOnly()
            if (clientResults.size != expectedClientCount) fail(
                "expected $expectedClientCount client results, got ${clientResults.size}\n" +
                    renderMultiPeerFailure(results),
            )
            for (client in clientResults) {
                val observed = client.data["observedChildren"]?.jsonPrimitive?.content?.toInt() ?: -1
                if (observed != 1) fail(
                    "client ${client.peerName} saw $observed children, expected 1\n${renderMultiPeerFailure(results)}",
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
