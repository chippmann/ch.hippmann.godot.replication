package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.NonManagedChildIgnoredScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class NonManagedChildIgnoredTest : FunSpec({
    test("adding an un-managed child does not propagate to clients") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 1
            orchestrator.launch(
                scenarioClass = NonManagedChildIgnoredScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = NonManagedChildIgnoredScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val client = results.clientsOnly().single()
            val observed = client.data["observedChildren"]?.jsonPrimitive?.content?.toInt() ?: -1
            if (observed != 1) fail(
                "client observed $observed children, expected exactly 1 (managed only)\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
