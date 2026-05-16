package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario

/**
 * Server spawns a managed scene **before** any client connects. A late-joining client
 * should still observe the existing child via the `peerSpawnAllForReplicated`
 * snapshot RPC fired when the authority sees its `onPeerSubscribed`.
 *
 * This exercises a code path the [SpawnScenario] doesn't — the snapshot replay
 * rather than the spawn-on-add broadcast.
 */
class LateJoinScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        context.startServer()

        // Spawn BEFORE clients connect. Clients will receive the spawn via the snapshot
        // RPC the library sends on peer subscription.
        val replicator = context.replicator
        val managedScene = replicator.managedScenes.first()
        replicator.addChild(managedScene.instantiate()!!.apply { setName("PreSpawnedChild") })

        context.put("preSpawnedChildCount", replicator.getChildCount().toInt())
        context.awaitClientsConnected(context.args.expectedClientCount)
        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 1 }
        context.put("observedChildren", replicator.getChildCount().toInt())
    }
}
