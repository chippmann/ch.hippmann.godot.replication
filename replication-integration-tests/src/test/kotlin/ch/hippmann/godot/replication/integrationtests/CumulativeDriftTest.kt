package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.CumulativeDriftScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class CumulativeDriftTest : FunSpec({
    test("tolerance predicate advances baseline only on send — cumulative drift eventually triggers") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = CumulativeDriftScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = CumulativeDriftScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val server = results.serverResult()
            val client = results.clientsOnly().single()
            val serverValue = server.data["serverValue"]?.jsonPrimitive?.content?.toInt() ?: -1
            val observedValue = client.data["observedValue"]?.jsonPrimitive?.content?.toInt() ?: -1
            // Values are scaled ×10 (2.5 → 25) so we can compare ints without fp noise.
            if (serverValue != 25 || observedValue != 25) fail(
                "expected server=25 (2.5) and observed=25 (2.5), got server=$serverValue, observed=$observedValue\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
