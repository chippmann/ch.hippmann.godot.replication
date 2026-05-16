package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestUnreliableSynced
import godot.core.Vector3
import kotlinx.coroutines.delay

/**
 * Same shape as [FrameSyncScenario] but the property is configured with
 * `UNRELIABLE_ORDERED` + `CHANNEL_5` so the runtime RPC dispatch goes through
 * `replicateForSynchronizedUnreliableOrderedChannel5`. Localhost ENet is effectively
 * lossless so convergence to the final value remains a reasonable assertion.
 */
class UnreliableSyncScenario : TestScenario {
    override val scenePath: String = "res://scenes/synchronized_unreliable.tscn"

    private val updateCount = 60
    private val expectedFinalX = (updateCount - 1).toDouble()

    private fun TestContext.synced(): IntegrationTestUnreliableSynced =
        runner.getNodeOrNull("Synced") as? IntegrationTestUnreliableSynced
            ?: error("synchronized_unreliable.tscn must contain a 'Synced' node of IntegrationTestUnreliableSynced")

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val synced = context.synced()
        for (frame in 0 until updateCount) {
            synced.customPosition = Vector3(frame.toDouble(), 0.0, 0.0)
            delay(16)
        }
        context.put("finalServerX", synced.customPosition.x.toInt())
        delay(1_000)
        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val synced = context.synced()
        context.pollUntil(timeoutMs = 30_000) {
            synced.customPosition.x >= expectedFinalX - 0.5
        }
        context.put("observedClientX", synced.customPosition.x.toInt())
    }
}
