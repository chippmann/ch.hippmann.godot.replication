package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import godot.api.PackedScene
import godot.api.ResourceLoader
import godot.core.variantArrayOf
import kotlinx.coroutines.delay

/**
 * Pins bug #6: the Replicator's `managedScenes` setter used to *accumulate* into the
 * internal `_managedScenes` map instead of replacing it. After reassigning to a
 * different list, adding an instance of a scene that was on the OLD list would still
 * replicate because its resource path was still in the lookup table.
 *
 * Scenario: scene starts with `managed_scenes=[cube]`. Server reassigns to
 * `[cube_alt]`, then adds one instance of each. Only the `cube_alt` instance should
 * reach the clients — exactly 1 child after both addChild calls.
 */
class ManagedScenesReassignmentScenario : TestScenario {
    // Scene starts with BOTH cube and cube_alt registered so that the CLIENT (whose
    // managedScenes is set by the loaded scene and never reassigned) is able to
    // instantiate either one when a spawn RPC arrives. Reassignment is a server-side
    // filter on what's ALLOWED to replicate outward.
    override val scenePath: String = "res://scenes/replication_two_managed.tscn"

    private val cubePath = "res://fixtures/cube.tscn"
    private val cubeAltPath = "res://fixtures/cube_alt.tscn"

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 5_000) {
            replicator.listeningPeers.size == context.args.expectedClientCount
        }

        val cubeScene = ResourceLoader.load(cubePath) as PackedScene
        val cubeAltScene = ResourceLoader.load(cubeAltPath) as PackedScene

        // Sanity: scene starts pre-configured with cube only.
        context.put("initialManagedCount", replicator.managedScenes.size)

        // Reassign to cube_alt only. After bug #6 fix, the old cube path is dropped
        // from the internal lookup map.
        replicator.managedScenes = variantArrayOf<PackedScene>(cubeAltScene)

        // Adding an instance of the OLD scene must NOT trigger spawn-on-add to peers.
        val cubeInstance = cubeScene.instantiate()!!.apply { setName("StaleCubeInstance") }
        replicator.addChild(cubeInstance)

        // Adding an instance of the NEW scene SHOULD trigger spawn-on-add.
        val altInstance = cubeAltScene.instantiate()!!.apply { setName("FreshAltInstance") }
        replicator.addChild(altInstance)

        // Server has both children locally (addChild is unconditional).
        context.put("serverChildren", replicator.getChildCount().toInt())

        // Give the spawn RPC propagation time before clients sample.
        delay(2_000)
        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 1 }

        // Give a settling window for any erroneous second RPC to arrive.
        delay(2_000)

        context.put("observedChildren", replicator.getChildCount().toInt())
        // Distinguish which one made it through.
        context.put("observedStaleCube", if (replicator.getNodeOrNull("StaleCubeInstance") != null) 1 else 0)
        context.put("observedFreshAlt", if (replicator.getNodeOrNull("FreshAltInstance") != null) 1 else 0)
    }
}
