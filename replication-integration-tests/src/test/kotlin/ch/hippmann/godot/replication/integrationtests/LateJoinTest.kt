package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.LateJoinScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class LateJoinTest : FunSpec({
    test("client joining after server has already spawned a managed child observes it via snapshot") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = LateJoinScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )

            // Give the server time to start listening AND spawn the managed scene
            // before any client connects. This is the entire point of the scenario.
            Thread.sleep(2_000)

            orchestrator.launch(
                scenarioClass = LateJoinScenario::class,
                role = Role.CLIENT,
                peerName = "late-client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val client = results.clientsOnly().single()
            val observed = client.data["observedChildren"]?.jsonPrimitive?.content?.toInt() ?: -1
            if (observed != 1) fail(
                "late-joining client observed $observed children, expected 1\n${renderMultiPeerFailure(results)}",
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
