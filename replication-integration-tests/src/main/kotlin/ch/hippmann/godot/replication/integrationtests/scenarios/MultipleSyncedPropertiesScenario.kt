package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestMultiSynced
import godot.core.Vector3
import kotlinx.coroutines.delay

/**
 * Synchronized node with three properties at three different tick intervals
 * (16ms / 32ms / 100ms) and three serializer paths (Vector3, Int, String). The
 * Synchronizer should launch one ticker coroutine per distinct tick group, so this
 * scenario also exercises that property of the implementation indirectly.
 *
 * Server sets all three properties to non-default values; each client should
 * converge to all three.
 */
class MultipleSyncedPropertiesScenario : TestScenario {
    override val scenePath: String = "res://scenes/synchronized_multi.tscn"

    // CAREFUL: scenario classes are loaded reflectively on the orchestrator JVM side
    // too (to read scenePath at launch time). That classpath does NOT have godot-api.
    // Field initializers must therefore stick to primitives / String — anything from
    // godot.core.* would NoClassDefFoundError before runAsServer ever runs.
    private val targetCounter = 42
    private val targetLabel = "synchronized"
    private val targetPositionX = 1.5

    private fun TestContext.synced(): IntegrationTestMultiSynced =
        runner.getNodeOrNull("Synced") as? IntegrationTestMultiSynced
            ?: error("synchronized_multi.tscn must contain a 'Synced' node of IntegrationTestMultiSynced")

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val synced = context.synced()
        synced.position = Vector3(targetPositionX, 2.5, 3.5)
        synced.counter = targetCounter
        synced.label = targetLabel

        // Give the lowest-frequency property (100ms tick) at least a few cycles.
        delay(1_500)
        context.put("serverPositionX", synced.position.x.toInt())
        context.put("serverCounter", synced.counter)
        context.put("serverLabel", synced.label)

        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val synced = context.synced()
        context.pollUntil(timeoutMs = 10_000) {
            synced.position.x >= targetPositionX - 0.01 &&
                synced.counter == targetCounter &&
                synced.label == targetLabel
        }
        context.put("observedPositionX", synced.position.x.toInt())
        context.put("observedCounter", synced.counter)
        context.put("observedLabel", synced.label)
    }
}
