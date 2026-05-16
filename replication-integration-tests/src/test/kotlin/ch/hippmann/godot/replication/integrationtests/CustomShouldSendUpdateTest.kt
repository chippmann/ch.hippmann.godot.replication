package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.CustomShouldSendUpdateScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class CustomShouldSendUpdateTest : FunSpec({
    test("custom shouldSendUpdate predicate filters intermediate values below the threshold") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = CustomShouldSendUpdateScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = CustomShouldSendUpdateScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val server = results.serverResult()
            val client = results.clientsOnly().single()
            val serverValue = server.data["serverValue"]?.jsonPrimitive?.content?.toInt() ?: -1
            val observedValue = client.data["observedValue"]?.jsonPrimitive?.content?.toInt() ?: -1
            if (serverValue != 4 || observedValue != 4) fail(
                "expected serverValue=4 and observedValue=4, got server=$serverValue, observed=$observedValue\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
