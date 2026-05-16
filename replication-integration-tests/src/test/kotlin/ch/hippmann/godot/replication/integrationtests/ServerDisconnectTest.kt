package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ServerDisconnectScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ServerDisconnectTest : FunSpec({
    test("clients observe serverDisconnected when the server closes its multiplayer peer") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            orchestrator.launch(
                scenarioClass = ServerDisconnectScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = ServerDisconnectScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            for (client in results.clientsOnly()) {
                val disconnected = client.data["disconnected"]?.jsonPrimitive?.content?.toBoolean() ?: false
                if (!disconnected) fail(
                    "client ${client.peerName} did not observe serverDisconnected\n${renderMultiPeerFailure(results)}",
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
