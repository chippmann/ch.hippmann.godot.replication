package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestSynchronized
import godot.core.Vector3
import kotlinx.coroutines.delay

/**
 * Server walks a Vector3 from (0,0,0) to (UPDATE_COUNT-1, 0, 0) one increment per
 * ~engine frame. Clients should converge on the final value via the Synchronizer's
 * per-tick RPCs.
 *
 * This is the "value changes every frame" sync scenario the test plan calls out
 * explicitly — it stresses the send/receive queue, the per-property serializer, and
 * the shouldSendUpdate de-duplication.
 */
@TestScene("res://scenes/synchronized_basic.tscn")
class FrameSyncScenario : TestScenario {
    private val updateCount = 60
    private val expectedFinalX = (updateCount - 1).toDouble()

    private fun TestContext.synchronizedNode(): IntegrationTestSynchronized =
        runner.getNodeOrNull("Synced") as? IntegrationTestSynchronized
            ?: error("synchronized_basic.tscn must contain a 'Synced' node of type IntegrationTestSynchronized")

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val synced = context.synchronizedNode()

        for (frame in 0 until updateCount) {
            synced.customPosition = Vector3(frame.toDouble(), 0.0, 0.0)
            delay(16) // ~60 Hz, matches the property's tick configuration
        }

        context.put("finalServerX", synced.customPosition.x.toInt())

        // Give the last few queued updates time to drain through _process and reach peers.
        delay(1_000)

        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val synced = context.synchronizedNode()

        // Convergence: poll until the client has caught up to (or past) the last value
        // the server should have sent. Don't require EXACT equality because subsequent
        // updates from the server may still be in flight.
        context.pollUntil(timeoutMs = 30_000) {
            synced.customPosition.x >= expectedFinalX - 0.5
        }
        context.put("observedClientX", synced.customPosition.x.toInt())
    }
}
