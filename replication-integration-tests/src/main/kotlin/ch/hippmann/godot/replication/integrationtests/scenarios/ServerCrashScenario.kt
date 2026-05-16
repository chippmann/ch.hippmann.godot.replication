package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import kotlinx.coroutines.delay

/**
 * Server starts and waits indefinitely; the orchestrator will force-kill it mid-test.
 * Clients should observe the `serverDisconnected` signal within a reasonable window.
 *
 * The server's exit will be non-zero (force-killed) so its result file may be missing.
 * The test must NOT assertAllPassed — only the client peers are expected to pass.
 */
class ServerCrashScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)
        // Hang until the orchestrator kills us. 5 minutes is well past the test timeout.
        delay(5 * 60_000L)
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()
        context.put("connected", true)

        context.awaitServerDisconnected(timeoutMs = 30_000)
        context.put("disconnected", true)
    }
}
