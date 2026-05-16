package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ReconnectScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ReconnectTest : FunSpec({
    test("client that disconnects and reconnects to the same server receives the snapshot again") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = ReconnectScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = ReconnectScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val client = results.clientsOnly().single()
            val first = client.data["firstConnectChildren"]?.jsonPrimitive?.content?.toInt() ?: -1
            val again = client.data["reconnectChildren"]?.jsonPrimitive?.content?.toInt() ?: -1
            if (first != 2 || again != 2) fail(
                "expected first=2 and reconnect=2, got first=$first, reconnect=$again\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
