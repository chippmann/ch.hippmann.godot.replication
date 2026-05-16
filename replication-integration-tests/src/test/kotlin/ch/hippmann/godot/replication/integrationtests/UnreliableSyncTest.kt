package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.UnreliableSyncScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class UnreliableSyncTest : FunSpec({
    test("UNRELIABLE_ORDERED sync on CHANNEL_5 converges (runtime path through one of the 10 generated RPC methods)") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            orchestrator.launch(
                scenarioClass = UnreliableSyncScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = UnreliableSyncScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 90)
            results.assertAllPassed()

            for (client in results.clientsOnly()) {
                val observed = client.data["observedClientX"]?.jsonPrimitive?.content?.toInt() ?: -1
                if (observed < 57 || observed > 60) fail(
                    "client ${client.peerName} converged to x=$observed, expected ~59 (allowing wider tail because unreliable)\n" +
                        renderMultiPeerFailure(results),
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
