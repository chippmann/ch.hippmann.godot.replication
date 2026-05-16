package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ClientDisconnectScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ClientDisconnectTest : FunSpec({
    test("server's listeningPeers shrinks when a client closes its multiplayer peer") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = ClientDisconnectScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = ClientDisconnectScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val server = results.serverResult()
            val peersAfterConnect = server.data["peersAfterConnect"]?.jsonPrimitive?.content?.toInt() ?: -1
            val peersAfterDisconnect = server.data["peersAfterDisconnect"]?.jsonPrimitive?.content?.toInt() ?: -1
            val listeningPeersAfterConnect = server.data["listeningPeersAfterConnect"]?.jsonPrimitive?.content?.toInt() ?: -1
            val listeningPeersAfterDisconnect = server.data["listeningPeersAfterDisconnect"]?.jsonPrimitive?.content?.toInt() ?: -1

            if (peersAfterConnect != 1 || peersAfterDisconnect != 0) fail(
                "multiplayer.getPeers expected 1 then 0, got $peersAfterConnect then $peersAfterDisconnect\n" +
                    renderMultiPeerFailure(results),
            )
            if (listeningPeersAfterConnect != 1 || listeningPeersAfterDisconnect != 0) fail(
                "Replicator.listeningPeers expected 1 then 0, got $listeningPeersAfterConnect then $listeningPeersAfterDisconnect\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
