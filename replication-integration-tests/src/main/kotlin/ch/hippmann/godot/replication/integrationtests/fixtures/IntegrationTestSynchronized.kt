package ch.hippmann.godot.replication.integrationtests.fixtures

import ch.hippmann.godot.replication.SyncConfig
import ch.hippmann.godot.replication.SyncConfigs
import ch.hippmann.godot.replication.Synchronized
import ch.hippmann.godot.replication.Synchronizer
import ch.hippmann.godot.replication.syncConfig
import godot.annotation.RegisterClass
import godot.annotation.RegisterFunction
import godot.api.Node
import godot.core.Vector3

/**
 * Test fixture demonstrating Synchronized usage the way the README documents:
 * call [initSynchronization] from `_enterTree`, call [performSynchronization] from
 * `_process` so queued RPCs actually leave on the main thread.
 *
 * Holds a single Vector3 property whose updates are sent every ~16ms (one engine
 * frame at 60 Hz) over a reliable channel. Reliable rather than unreliable so the
 * test is deterministic — the convergence check doesn't need to tolerate dropped
 * packets on loopback.
 */
@RegisterClass
class IntegrationTestSynchronized : Node(), Synchronized by Synchronizer() {
    var customPosition: Vector3 = Vector3()

    override val syncConfig: SyncConfigs = syncConfig {
        property(::customPosition) {
            tick = 16
            syncMethod = SyncConfig.SyncMethod.RELIABLE
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
