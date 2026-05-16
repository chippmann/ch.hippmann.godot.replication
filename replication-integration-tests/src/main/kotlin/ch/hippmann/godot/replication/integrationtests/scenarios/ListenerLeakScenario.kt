package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.autoload.RemoteListenerReadyRedirector
import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestReplicator
import kotlinx.coroutines.delay

/**
 * Single-process, no multiplayer needed: add N Replicators dynamically, observe the
 * autoload's `listenerCount` grow, queueFree them, and assert the count returns to
 * baseline. Pins bug #5 — the autoload used to leak entries because the
 * `notificationOnExitingTreeForWithRemoteListeners` hook never called `deregister`.
 *
 * This scenario uses the empty scene (TestRunner only) so the baseline is well-defined.
 */
class ListenerLeakScenario : TestScenario {
    override val scenePath: String = "res://scenes/empty.tscn"

    private val replicatorCount = 5

    override suspend fun runAsServer(context: TestContext) {
        val initialCount = RemoteListenerReadyRedirector.listenerCount
        context.put("initialListenerCount", initialCount)

        val replicators = (1..replicatorCount).map { index ->
            IntegrationTestReplicator().apply { setName("Replicator$index") }
        }
        replicators.forEach { context.runner.addChild(it) }

        // Wait for _ready → notifyReady on each.
        context.pollUntil(timeoutMs = 5_000) {
            RemoteListenerReadyRedirector.listenerCount == initialCount + replicatorCount
        }
        context.put("peakListenerCount", RemoteListenerReadyRedirector.listenerCount)

        // Remove them all. queueFree is deferred; tree_exiting + our deregister hook fire next frame.
        replicators.forEach { it.queueFree() }

        context.pollUntil(timeoutMs = 5_000) {
            RemoteListenerReadyRedirector.listenerCount == initialCount
        }
        context.put("finalListenerCount", RemoteListenerReadyRedirector.listenerCount)

        // Give one more beat so the test isn't racy w.r.t. anything queued.
        delay(100)
    }

    override suspend fun runAsClient(context: TestContext) {
        error("ListenerLeakScenario is single-peer; no client role")
    }
}
