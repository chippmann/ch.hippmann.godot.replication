package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ForgedSyncScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ForgedSyncTest : FunSpec({
    test("non-authority peer cannot forge a sync update to another peer") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            orchestrator.launch(
                scenarioClass = ForgedSyncScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = ForgedSyncScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            for (client in results.clientsOnly()) {
                val legitimate = client.data["legitimateX"]?.jsonPrimitive?.content?.toInt() ?: -1
                val final = client.data["finalX"]?.jsonPrimitive?.content?.toInt() ?: -1
                val forgedRecipients = client.data["forgedRecipients"]?.jsonPrimitive?.content?.toInt() ?: -1

                if (legitimate != 42) fail(
                    "client ${client.peerName} never saw legitimate value 42, got $legitimate\n" +
                        renderMultiPeerFailure(results),
                )
                if (forgedRecipients < 1) fail(
                    "client ${client.peerName} found no other client to forge to (expected ≥1)\n" +
                        renderMultiPeerFailure(results),
                )
                if (final != 42) fail(
                    "client ${client.peerName} customPosition.x was tampered: legitimate=42, final=$final\n" +
                        renderMultiPeerFailure(results),
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
