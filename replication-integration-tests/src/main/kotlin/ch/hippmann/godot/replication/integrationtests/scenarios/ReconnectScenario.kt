package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import godot.api.ENetMultiplayerPeer
import godot.api.PackedScene
import godot.api.ResourceLoader
import godot.core.Error
import kotlinx.coroutines.delay

/**
 * Server spawns two managed children up front and never touches them again. The
 * single client:
 *   1. Connects, observes both spawns (via the snapshot RPC fired on subscribe)
 *   2. Closes its multiplayer peer (clean disconnect)
 *   3. Creates a fresh ENetMultiplayerPeer and reconnects to the same server
 *   4. Observes both spawns AGAIN — confirming the server's onPeerSubscribed
 *      catch-up fires on every fresh subscribe, not just the first one
 *
 * Pins the resubscribe path that bug #9 (peerSpawnAll reconcile) and the
 * symmetric WithRemoteListeners handshake fix (#4) jointly depend on.
 */
class ReconnectScenario : TestScenario {
    private val managedScenePath = "res://fixtures/cube.tscn"

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val replicator = context.replicator
        val packed = ResourceLoader.load(managedScenePath) as PackedScene
        replicator.addChild(packed.instantiate()!!.apply { setName("ChildA") })
        replicator.addChild(packed.instantiate()!!.apply { setName("ChildB") })

        // Stay alive long enough for the client to: receive the snapshot,
        // disconnect, reconnect, receive the snapshot AGAIN, and report.
        delay(10_000)
        context.put("serverChildCount", replicator.getChildCount().toInt())
    }

    override suspend fun runAsClient(context: TestContext) {
        val replicator = context.replicator

        // First connection.
        context.connectToServer()
        context.awaitServerConnected()
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 2 }
        context.put("firstConnectChildren", replicator.getChildCount().toInt())

        // Clean disconnect. We don't await server confirmation — the server-side
        // peer_disconnected fires on its own; the client just moves on.
        context.multiplayer.multiplayerPeer?.close()

        // Wait for the local Replicator's managed children to clear out — when the
        // client side observes server_disconnected, the existing managed children
        // remain in the tree (the library doesn't auto-prune on disconnect). Despawn
        // them manually so the reconnect snapshot has somewhere to land freshly.
        replicator.getChildren().toList().forEach { it.queueFree() }
        delay(300)

        // Fresh peer; reconnect to the same server.
        val peer = ENetMultiplayerPeer()
        val error = peer.createClient(address = "127.0.0.1", port = context.args.port)
        check(error == Error.OK) { "reconnect createClient failed: $error" }
        context.multiplayer.multiplayerPeer = peer
        context.awaitServerConnected()
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 2 }
        context.put("reconnectChildren", replicator.getChildCount().toInt())
    }
}
