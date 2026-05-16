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
 * Test fixture with three properties at three different tick intervals. Exercises:
 *  - multiple synced properties on a single Synchronized node
 *  - different tick groupings (Synchronizer launches one coroutine per tick group)
 *  - different serializer paths (Vector3, primitive Int, primitive String)
 */
@RegisterClass
class IntegrationTestMultiSynced : Node(), Synchronized by Synchronizer() {
    var position: Vector3 = Vector3()
    var counter: Int = 0
    var label: String = ""

    override val syncConfig: SyncConfigs = syncConfig {
        property(::position) {
            tick = 16
            syncMethod = SyncConfig.SyncMethod.RELIABLE
        }
        property(::counter) {
            tick = 32
            syncMethod = SyncConfig.SyncMethod.RELIABLE
        }
        property(::label) {
            tick = 100
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
