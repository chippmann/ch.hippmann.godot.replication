package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestThresholdSynced
import kotlinx.coroutines.delay

/**
 * Pins the DSL's custom `shouldSendUpdate` predicate surface.
 *
 * Server walks the property through four values: 0.0 → 0.5 → 2.0 → 2.3 → 4.0
 * with the tick interval (16ms) elapsing between each. The predicate fires only
 * when |delta| > 1.0, so:
 *
 *   tick after  0.5: first eval — `fromLastSync` is null on first check → true → send 0.5
 *   tick after  2.0: delta 1.5 → send 2.0
 *   tick after  2.3: delta 0.3 → SUPPRESSED
 *   tick after  4.0: delta 1.7 → send 4.0
 *
 * The client should converge on 4.0 (not 2.3). The test asserts that, plus
 * `serverValue == 4.0` for sanity.
 */
@TestScene("res://scenes/synchronized_threshold.tscn")
class CustomShouldSendUpdateScenario : TestScenario {
    private fun TestContext.synced(): IntegrationTestThresholdSynced =
        runner.getNodeOrNull("Synced") as? IntegrationTestThresholdSynced
            ?: error("synchronized_threshold.tscn must contain a 'Synced' node of IntegrationTestThresholdSynced")

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)
        val synced = context.synced()

        // Wait for the WithRemoteListeners handshake on the synced node before
        // changing the value — otherwise the catch-up sendFullStateTo on subscribe
        // would race the first mutation.
        context.pollUntil(timeoutMs = 5_000) {
            synced.listeningPeers.size == context.args.expectedClientCount
        }

        synced.value = 0.5
        delay(80) // > tick, enough cycles for the send-or-suppress decision to fire
        synced.value = 2.0
        delay(80)
        synced.value = 2.3
        delay(80)
        synced.value = 4.0
        delay(2_000) // give last send time to propagate before we exit

        context.put("serverValue", synced.value.toInt())
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()
        val synced = context.synced()

        // Wait until the final value (4.0) has arrived. If the predicate were broken
        // and intermediate small deltas (e.g. 2.3) somehow won out, the client would
        // settle on the wrong value.
        context.pollUntil(timeoutMs = 10_000) { synced.value >= 3.5 }
        context.put("observedValue", synced.value.toInt())
    }
}
