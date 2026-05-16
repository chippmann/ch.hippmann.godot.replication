package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestSynchronized
import godot.core.Vector3
import kotlinx.coroutines.delay

/**
 * The managed scene's root is itself a `Synchronized` node. Server instantiates the
 * managed scene, sets `customPosition` BEFORE adding it under the Replicator, then
 * addChild. The library's spawn-on-add RPC must carry the property's initial value
 * via `syncConfig.serializeSpawnData()`. Each client should observe the same
 * customPosition immediately after the spawn propagates — no per-tick sync window
 * required.
 */
class SyncOnSpawnScenario : TestScenario {
    override val scenePath: String = "res://scenes/replication_synced_managed.tscn"

    private val initialX = 10.0
    private val initialY = 20.0
    private val initialZ = 30.0

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val replicator = context.replicator
        // Wait for the WithRemoteListeners handshake to complete so clients are subscribed.
        context.pollUntil(timeoutMs = 5_000) {
            replicator.listeningPeers.size == context.args.expectedClientCount
        }

        val packed = replicator.managedScenes.first()
        val instance = packed.instantiate() as IntegrationTestSynchronized
        instance.setName("SyncedInstance")
        instance.customPosition = Vector3(initialX, initialY, initialZ)
        replicator.addChild(instance)

        context.put("serverPos", positionTriple(instance))

        // Give the spawn RPC + spawnData unpack time on every client.
        delay(2_000)
        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 1 }

        val instance = replicator.getNodeOrNull("SyncedInstance") as? IntegrationTestSynchronized
            ?: error("expected SyncedInstance under Replicator after spawn")

        // Small grace so applySpawnData has fired through _process.
        delay(500)
        context.put("observedPos", positionTriple(instance))
    }

    private fun positionTriple(node: IntegrationTestSynchronized): String =
        "${node.customPosition.x.toInt()},${node.customPosition.y.toInt()},${node.customPosition.z.toInt()}"
}
