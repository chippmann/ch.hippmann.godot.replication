package ch.hippmann.godot.replication.integrationtests.fixtures

import ch.hippmann.godot.replication.SyncConfig
import ch.hippmann.godot.replication.SyncConfigs
import ch.hippmann.godot.replication.Synchronized
import ch.hippmann.godot.replication.Synchronizer
import ch.hippmann.godot.replication.syncConfig
import godot.annotation.RegisterClass
import godot.annotation.RegisterFunction
import godot.api.Node
import kotlin.math.abs

/**
 * Pins the DSL's `shouldSendUpdate` custom-predicate surface. The property
 * deliberately swaps the default `current != fromLastSync` rule for a tolerance-
 * based check — only fire a sync when the value has changed by **more than 1.0**.
 * The README documents this exact pattern for `Vector3.isEqualApprox` etc.
 *
 * `CustomShouldSendUpdateScenario` exercises the runtime effect: small changes
 * (delta ≤ 1.0) are filtered, big changes propagate.
 */
@RegisterClass
class IntegrationTestThresholdSynced : Node(), Synchronized by Synchronizer() {
    var value: Double = 0.0

    override val syncConfig: SyncConfigs = syncConfig {
        property(::value) {
            tick = 16
            syncMethod = SyncConfig.SyncMethod.RELIABLE
            shouldSendUpdate = { current, fromLastSync ->
                abs(current - fromLastSync) > 1.0
            }
        }
    }

    @RegisterFunction
    override fun _enterTree() {
        initSynchronization()
    }

    @RegisterFunction
    override fun _process(delta: Double) {
        performSynchronization()
    }
}
