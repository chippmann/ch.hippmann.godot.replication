package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ClientCrashScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ClientCrashTest : FunSpec({
    test("server's listeningPeers shrinks when a client process is force-killed") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = ClientCrashScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )
            Thread.sleep(300)
            val client = orchestrator.launch(
                scenarioClass = ClientCrashScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            // Let the client connect and complete the subscribe handshake.
            Thread.sleep(3_000)

            orchestrator.killPeer(client)

            val results = orchestrator.awaitAll(timeoutSeconds = 60)

            // Client was force-killed — its result file is missing, that's fine.
            val server = results.serverResult()
            if (!server.passed) fail(
                "server did not pass\n${renderMultiPeerFailure(results)}",
            )
            val peersAfterConnect = server.data["peersAfterConnect"]?.jsonPrimitive?.content?.toInt() ?: -1
            val peersAfterCrash = server.data["peersAfterCrash"]?.jsonPrimitive?.content?.toInt() ?: -1
            val listeningPeersAfterConnect = server.data["listeningPeersAfterConnect"]?.jsonPrimitive?.content?.toInt() ?: -1
            val listeningPeersAfterCrash = server.data["listeningPeersAfterCrash"]?.jsonPrimitive?.content?.toInt() ?: -1

            if (peersAfterConnect != 1 || peersAfterCrash != 0) fail(
                "multiplayer.getPeers expected 1 then 0, got $peersAfterConnect then $peersAfterCrash\n" +
                    renderMultiPeerFailure(results),
            )
            if (listeningPeersAfterConnect != 1 || listeningPeersAfterCrash != 0) fail(
                "Replicator.listeningPeers expected 1 then 0, got $listeningPeersAfterConnect then $listeningPeersAfterCrash\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
