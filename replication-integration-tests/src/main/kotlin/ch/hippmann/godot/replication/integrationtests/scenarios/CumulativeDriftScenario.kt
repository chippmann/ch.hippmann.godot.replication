package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestThresholdSynced
import kotlinx.coroutines.delay

/**
 * Pins bug #21 — `lastSyncState` must advance only when a sync is actually sent,
 * not on every shouldSendUpdate evaluation.
 *
 * The threshold fixture suppresses sends with |delta| ≤ 1.0. Server makes a
 * sequence of small increments — each ≤ 1.0 from the IMMEDIATELY preceding value,
 * but each large enough that the gap from the original baseline (0.0) eventually
 * exceeds 1.0. With the broken (unconditional baseline advance) code, every tick
 * silently moved the baseline forward and no send ever fired. With the fix, once
 * the cumulative drift crosses 1.0 from the last-sent value, the send fires.
 *
 * Sequence: 0.0 → 0.4 (suppressed) → 0.8 (suppressed) → 1.2 (delta from 0.0 last-
 * sent = 1.2 > 1.0 → send) → 1.5 (delta from 1.2 = 0.3 → suppressed) → 2.5 (delta
 * from 1.2 = 1.3 → send). Final received value: 2.5.
 */
@TestScene("res://scenes/synchronized_threshold.tscn")
class CumulativeDriftScenario : TestScenario {
    private fun TestContext.synced(): IntegrationTestThresholdSynced =
        runner.getNodeOrNull("Synced") as? IntegrationTestThresholdSynced
            ?: error("synchronized_threshold.tscn must contain a 'Synced' node of IntegrationTestThresholdSynced")

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)
        val synced = context.synced()

        // Wait for the WithRemoteListeners handshake on the synced node before
        // starting — otherwise the catch-up sendFullStateTo on subscribe could
        // interleave with the first mutation.
        context.pollUntil(timeoutMs = 5_000) {
            synced.listeningPeers.size == context.args.expectedClientCount
        }

        // Six small steps. Per-step deltas are all ≤ 0.6 (well under threshold);
        // it's the cumulative drift from the last actually-sent value that matters.
        // Use a delay longer than the 16ms tick so each step gets evaluated.
        for (value in listOf(0.4, 0.8, 1.2, 1.5, 2.0, 2.5)) {
            synced.value = value
            delay(80)
        }

        delay(2_000) // let the last send propagate
        context.put("serverValue", (synced.value * 10).toInt()) // 25 means 2.5
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()
        val synced = context.synced()

        // Wait for the cumulative-drift send (final value 2.5).
        context.pollUntil(timeoutMs = 10_000) { synced.value >= 2.4 }
        context.put("observedValue", (synced.value * 10).toInt())
    }
}
