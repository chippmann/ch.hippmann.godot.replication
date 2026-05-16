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
 * Variant of [IntegrationTestSynchronized] that uses [SyncConfig.SyncMethod.UNRELIABLE_ORDERED]
 * on a non-default channel (CHANNEL_5). Exercises the runtime path through one of the
 * 10 generated `replicateForSynchronizedUnreliableOrderedChannel*` RPC methods —
 * dead code before bug #1 was fixed because the DSL didn't expose `syncChannel`.
 */
@RegisterClass
class IntegrationTestUnreliableSynced : Node(), Synchronized by Synchronizer() {
    var customPosition: Vector3 = Vector3()

    override val syncConfig: SyncConfigs = syncConfig {
        property(::customPosition) {
            tick = 16
            syncMethod = SyncConfig.SyncMethod.UNRELIABLE_ORDERED
            syncChannel = SyncConfig.SyncChannel.CHANNEL_5
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
