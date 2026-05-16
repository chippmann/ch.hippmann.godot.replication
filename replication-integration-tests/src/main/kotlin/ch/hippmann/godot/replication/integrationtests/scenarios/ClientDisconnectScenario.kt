package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import kotlinx.coroutines.delay

/**
 * Server waits for clients to connect AND complete the WithRemoteListeners handshake,
 * then polls until `multiplayer.getPeers()` is empty. The client side waits briefly
 * after connecting then closes its multiplayer peer cleanly — exits normally.
 *
 * Exercises `peerDisconnected` in `RemoteListenerManager`: `listeningPeers` should
 * be pruned and the `onPeerUnsubscribed` callbacks invoked. Library-side behaviour
 * is identical whether the client crashed or disconnected gracefully (same Godot
 * signal fires either way), so the graceful path is sufficient coverage without
 * spawning macOS crash dialogs.
 */
class ClientDisconnectScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)
        context.put("peersAfterConnect", context.multiplayer.getPeers().size)

        // The WithRemoteListeners handshake takes a few frames after the underlying ENet
        // peer_connected — wait for it before we sample listeningPeers.
        context.pollUntil(timeoutMs = 5_000) {
            context.replicator.listeningPeers.size == context.args.expectedClientCount
        }
        context.put("listeningPeersAfterConnect", context.replicator.listeningPeers.size)

        context.pollUntil(timeoutMs = 30_000) {
            context.multiplayer.getPeers().size == 0
        }
        context.put("peersAfterDisconnect", context.multiplayer.getPeers().size)
        // Give the unsubscribe RPC a beat to fire on our side, then re-read.
        delay(500)
        context.put("listeningPeersAfterDisconnect", context.replicator.listeningPeers.size)
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()
        // Stay connected briefly so the handshake completes server-side, then close
        // the multiplayer peer cleanly. ENet emits the disconnect packet, the server
        // observes peer_disconnected, listeningPeers shrinks.
        delay(2_000)
        context.multiplayer.multiplayerPeer?.close()
        delay(500) // let the close packet leave the socket before we exit
    }
}
