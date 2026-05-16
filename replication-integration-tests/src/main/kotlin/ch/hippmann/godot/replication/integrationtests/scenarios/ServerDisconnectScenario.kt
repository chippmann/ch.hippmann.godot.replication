package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import kotlinx.coroutines.delay

/**
 * Server waits for clients, then closes its multiplayer peer cleanly. Each client
 * should observe `serverDisconnected` regardless of whether the server crashed or
 * disconnected gracefully — both fire the same signal. Using graceful close keeps
 * macOS's crash reporter quiet while exercising the same library code path.
 */
class ServerDisconnectScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        // Let clients settle so each can record `connected = true` in their report.
        delay(2_000)

        // Drop the connection cleanly. ENet flushes a disconnect packet to each
        // client; each client's MultiplayerAPI fires `server_disconnected` (the
        // same signal a SIGKILL would have produced, just without the crash dialog).
        context.multiplayer.multiplayerPeer?.close()

        // Stay alive briefly so the close packet actually leaves the socket and the
        // clients can observe before we tear down.
        delay(2_000)
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()
        context.put("connected", true)

        context.awaitServerDisconnected(timeoutMs = 30_000)
        context.put("disconnected", true)
    }
}
