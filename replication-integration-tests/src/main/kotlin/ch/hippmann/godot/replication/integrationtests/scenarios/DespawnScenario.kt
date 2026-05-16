package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import kotlinx.coroutines.delay

/**
 * Server adds a managed child, all clients observe it, server then removes the child
 * via `queueFree()`. Clients must observe the child disappear, exercising
 * `Replicator.notificationOnChildExitingTreeForReplicated` and `peerDespawnForReplicated`.
 */
class DespawnScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val replicator = context.replicator
        val managedScene = replicator.managedScenes.first()
        val instance = managedScene.instantiate()!!.apply { setName("Doomed") }
        replicator.addChild(instance)

        context.put("childCountAfterSpawn", replicator.getChildCount().toInt())

        // Give clients time to observe the spawn (sync is RPC-paced).
        delay(500)

        instance.queueFree()

        // queueFree is deferred; give one frame for the local tree to actually drop it.
        delay(100)
        context.put("childCountAfterDespawn", replicator.getChildCount().toInt())

        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 1 }
        context.put("childrenAfterSpawn", replicator.getChildCount().toInt())

        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() == 0 }
        context.put("childrenAfterDespawn", replicator.getChildCount().toInt())
    }
}
