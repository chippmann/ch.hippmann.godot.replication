package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ServerCrashScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ServerCrashTest : FunSpec({
    test("clients observe serverDisconnected when the server process is force-killed") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            val server = orchestrator.launch(
                scenarioClass = ServerCrashScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = ServerCrashScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            // Let clients connect AND record their `connected = true`.
            Thread.sleep(3_000)

            // Pull the network out from under them.
            orchestrator.killPeer(server)

            val results = orchestrator.awaitAll(timeoutSeconds = 60)

            // Server was force-killed — its result file is missing. Check clients only.
            for (client in results.clientsOnly()) {
                if (!client.passed) fail(
                    "client ${client.peerName} did not pass\n${renderMultiPeerFailure(results)}",
                )
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
