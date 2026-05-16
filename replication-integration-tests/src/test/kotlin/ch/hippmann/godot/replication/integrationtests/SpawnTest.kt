package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.SpawnScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonPrimitive

class SpawnTest : FunSpec({
    test("server-spawned managed scene replicates to two clients") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2

            orchestrator.launch(
                scenarioClass = SpawnScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { clientIndex ->
                Thread.sleep(300) // small stagger so the server is already listening
                orchestrator.launch(
                    scenarioClass = SpawnScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${clientIndex + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val clientResults = results.clientsOnly()
            clientResults.size shouldBe expectedClientCount
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
