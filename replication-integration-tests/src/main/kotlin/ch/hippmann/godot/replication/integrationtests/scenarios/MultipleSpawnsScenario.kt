package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario

/**
 * Server adds multiple managed children in sequence; each client must observe all of
 * them. Exercises a tight sequence of `childEnteredTree` → `peerSpawnForReplicated`
 * RPCs without intervening peer reconnects.
 */
class MultipleSpawnsScenario : TestScenario {
    private val spawnCount = 5

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val replicator = context.replicator
        val managedScene = replicator.managedScenes.first()
        repeat(spawnCount) { index ->
            replicator.addChild(
                managedScene.instantiate()!!.apply { setName("Spawn$index") },
            )
        }

        context.put("spawnedCount", replicator.getChildCount().toInt())
        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= spawnCount }
        context.put("observedCount", replicator.getChildCount().toInt())
    }
}
