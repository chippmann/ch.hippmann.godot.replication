package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.FrameSyncScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class FrameSyncTest : FunSpec({
    test("server's per-frame Vector3 updates converge on clients") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            orchestrator.launch(
                scenarioClass = FrameSyncScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = FrameSyncScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 90)
            results.assertAllPassed()

            val server = results.serverResult()
            val finalServerX = server.data["finalServerX"]?.jsonPrimitive?.content?.toInt() ?: -1
            if (finalServerX != 59) fail("server expected to reach x=59, got $finalServerX")

            for (client in results.clientsOnly()) {
                val observed = client.data["observedClientX"]?.jsonPrimitive?.content?.toInt() ?: -1
                // Allow a few-frame lag; reliable RPC should leave us within a small tail.
                if (observed < 58 || observed > 60) fail(
                    "client ${client.peerName} converged to x=$observed, expected ~59\n" +
                        renderMultiPeerFailure(results),
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
