package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.MultipleSyncedPropertiesScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class MultipleSyncedPropertiesTest : FunSpec({
    test("Synchronized node with three properties at different tick intervals syncs all to clients") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            orchestrator.launch(
                scenarioClass = MultipleSyncedPropertiesScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = MultipleSyncedPropertiesScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            for (client in results.clientsOnly()) {
                val position = client.data["observedPositionX"]?.jsonPrimitive?.content?.toInt() ?: -1
                val counter = client.data["observedCounter"]?.jsonPrimitive?.content?.toInt() ?: -1
                val label = client.data["observedLabel"]?.jsonPrimitive?.content
                if (position != 1 || counter != 42 || label != "synchronized") fail(
                    "client ${client.peerName} converged to (position.x=$position, counter=$counter, label=$label); " +
                        "expected (1, 42, synchronized)\n${renderMultiPeerFailure(results)}",
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
