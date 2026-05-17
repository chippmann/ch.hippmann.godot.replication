package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestReplicator
import godot.api.PackedScene
import godot.api.ResourceLoader
import kotlinx.coroutines.delay

/**
 * Two Replicators in series: the OUTER (in the scene at `TestRunner/Replicator`)
 * has the INNER packed scene as a managed child. The inner scene itself is a
 * Replicator with the cube as its managed child.
 *
 * Server flow:
 *   1. Instantiate the inner Replicator packed scene; add it under the outer.
 *   2. Wait for the inner's WithRemoteListeners handshake to complete (so the
 *      client's mirror of the inner replicator subscribes to ours).
 *   3. Instantiate a cube; add it under the spawned inner Replicator.
 *
 * Client should observe:
 *   - 1 child under outer (the inner instance)
 *   - 1 child under that inner instance (the cube)
 *
 * Each level uses its own independent Replicator/handshake/subscription state.
 */
@TestScene("res://scenes/replication_nested.tscn")
class NestedReplicatorsScenario : TestScenario {
    private val innerScenePath = "res://fixtures/inner_replicator.tscn"
    private val cubeScenePath = "res://fixtures/cube.tscn"

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val outerReplicator = context.replicator
        // Wait for the OUTER handshake before spawning, otherwise the spawn-on-add
        // RPC fires into an empty listeningPeers set (see bug #4's class of issue).
        context.pollUntil(timeoutMs = 5_000) {
            outerReplicator.listeningPeers.size == context.args.expectedClientCount
        }

        val innerPacked = ResourceLoader.load(innerScenePath) as PackedScene
        val innerInstance = innerPacked.instantiate() as IntegrationTestReplicator
        innerInstance.setName("InnerInstance")
        outerReplicator.addChild(innerInstance)

        // Wait for the SPAWNED inner replicator's own handshake to complete with
        // the client's mirror of it. Each Replicator instance has its own
        // listeningPeers.
        context.pollUntil(timeoutMs = 10_000) {
            innerInstance.listeningPeers.size == context.args.expectedClientCount
        }

        val cubePacked = ResourceLoader.load(cubeScenePath) as PackedScene
        val cubeInstance = cubePacked.instantiate()!!.apply { setName("InnerCube") }
        innerInstance.addChild(cubeInstance)

        context.put("outerChildCount", outerReplicator.getChildCount().toInt())
        context.put("innerChildCount", innerInstance.getChildCount().toInt())

        delay(2_000) // let the cube spawn RPC propagate before clients sample
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val outerReplicator = context.replicator

        // Wait for the inner Replicator to be spawned under the outer.
        context.pollUntil(timeoutMs = 10_000) { outerReplicator.getChildCount() >= 1 }
        val innerInstance = outerReplicator.getNodeOrNull("InnerInstance") as? IntegrationTestReplicator
            ?: error("expected InnerInstance under outer Replicator")
        context.put("observedOuterChildCount", outerReplicator.getChildCount().toInt())

        // And then for the cube to land under the inner.
        context.pollUntil(timeoutMs = 10_000) { innerInstance.getChildCount() >= 1 }
        context.put("observedInnerChildCount", innerInstance.getChildCount().toInt())
        context.put("observedInnerHasCube", innerInstance.getNodeOrNull("InnerCube") != null)
    }
}
