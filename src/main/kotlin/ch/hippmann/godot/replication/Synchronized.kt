package ch.hippmann.godot.replication

import godot.Node
import godot.annotation.RegisterFunction
import godot.annotation.Rpc


interface Synchronized: WithRemoteListeners {

    val syncConfig: SyncConfigs

    fun <T> T.initSynchronization() where T : Node, T: Synchronized

    fun performSynchronization()

    @RegisterFunction
    fun notificationOnReadyForSynchronized()

    @Rpc
    @RegisterFunction
    fun replicateForSynchronized(fqName: String, data: String)
}
