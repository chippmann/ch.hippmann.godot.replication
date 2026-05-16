package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import kotlinx.coroutines.delay

/**
 * Server waits for clients to connect, then polls until `multiplayer.getPeers()` is
 * empty (i.e. all clients have gone away). The orchestrator force-kills the clients
 * after a brief connected window; the server must observe their absence within a
 * reasonable window.
 *
 * Exercises the `peerDisconnected` path in `RemoteListenerManager` — `listeningPeers`
 * should be pruned and the onPeerUnsubscribed callbacks invoked.
 */
class ClientCrashScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)
        context.put("peersAfterConnect", context.multiplayer.getPeers().size)

        // The WithRemoteListeners handshake takes a few frames after the underlying ENet
        // peer_connected — wait for it to complete before we sample listeningPeers.
        context.pollUntil(timeoutMs = 5_000) {
            context.replicator.listeningPeers.size == context.args.expectedClientCount
        }
        context.put("listeningPeersAfterConnect", context.replicator.listeningPeers.size)

        context.pollUntil(timeoutMs = 30_000) {
            context.multiplayer.getPeers().size == 0
        }
        context.put("peersAfterCrash", context.multiplayer.getPeers().size)
        // Give the unsubscribe RPC a beat to fire, then re-read.
        delay(500)
        context.put("listeningPeersAfterCrash", context.replicator.listeningPeers.size)
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()
        // Hang; orchestrator will force-kill.
        delay(5 * 60_000L)
    }
}
