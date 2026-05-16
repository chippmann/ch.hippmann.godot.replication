package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.MultipleSpawnsScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class MultipleSpawnsTest : FunSpec({
    test("server adding multiple managed children in sequence replicates them all") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            orchestrator.launch(
                scenarioClass = MultipleSpawnsScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = MultipleSpawnsScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            for (client in results.clientsOnly()) {
                val observed = client.data["observedCount"]?.jsonPrimitive?.content?.toInt() ?: -1
                if (observed != 5) fail(
                    "client ${client.peerName} observed $observed spawns, expected 5\n" +
                        renderMultiPeerFailure(results),
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
