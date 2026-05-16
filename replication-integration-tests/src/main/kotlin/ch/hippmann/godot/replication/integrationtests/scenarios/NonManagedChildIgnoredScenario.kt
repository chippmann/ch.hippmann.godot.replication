package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import godot.api.Node
import kotlinx.coroutines.delay

/**
 * Server adds two things under the Replicator: (1) a managed scene instance (which
 * SHOULD replicate), and (2) a plain Node with no scene file path (which should NOT).
 * Verifies the library's `provideManagedSceneFromNode` filter actually excludes the
 * un-managed child from the RPC broadcast.
 */
class NonManagedChildIgnoredScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val replicator = context.replicator

        // (1) Managed — should replicate.
        replicator.addChild(
            replicator.managedScenes.first().instantiate()!!.apply { setName("ManagedInstance") },
        )
        // (2) Plain Node with no sceneFilePath — should be ignored.
        replicator.addChild(Node().apply { setName("BareNode") })

        context.put("serverChildCount", replicator.getChildCount().toInt())

        // Hold long enough that any (incorrect) RPC would have landed.
        delay(1_500)
        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 1 }

        // Give extra time to confirm no SECOND child shows up.
        delay(1_500)
        context.put("observedChildren", replicator.getChildCount().toInt())
    }
}
