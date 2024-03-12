package ch.hippmann.godot.replication

import ch.hippmann.godot.utilities.coroutines.scope.DefaultGodotCoroutineScope
import ch.hippmann.godot.utilities.coroutines.scope.GodotCoroutineScope
import ch.hippmann.godot.utilities.logging.debug
import godot.Node
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class Synchronizer : Synchronized, WithRemoteListeners by RemoteListenerManager(), WithNodeAccess by WithNodeAccessDelegate(), GodotCoroutineScope by DefaultGodotCoroutineScope() {
    private var tickToConfigs: Map<Long, SyncConfigs> = mapOf()
    override var syncConfig: SyncConfigs = mutableMapOf()
        set(value) {
            field = value
            tickToConfigs = value
                    .map { (fqName, config) ->
                        config.tick to (fqName to config)
                    }
                    .groupBy { (tick, _) -> tick }
                    .mapValues { (_, values) ->
                        values
                                .associate { value -> value.second }
                                .filterValues { config -> config.syncOnTick }
                    }
        }

    private val sendQueue: Queue<() -> Unit> = LinkedList()
    private val receiveQueue: Queue<() -> Unit> = LinkedList()

    override fun <T> T.initSynchronization() where T : Node, T : Synchronized {
        initNodeAccess()
        initListening()

        // the delegate (this class) cannot access properties overridden by the implementer. So we cannot get its
        // config. Thus, we manually assign it here to whatever the implementer defined
        this@Synchronizer.syncConfig = this.syncConfig

        this.ready.connect(this, Synchronized::notificationOnReadyForSynchronized)
        debug { "Synchronizer[${this.name}]: initialised" }
    }

    override fun performSynchronization() {
        while (receiveQueue.isNotEmpty()) {
            try {
                receiveQueue.poll()?.invoke()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        while (sendQueue.isNotEmpty()) {
            try {
                sendQueue.poll()?.invoke()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun notificationOnReadyForSynchronized() {
        ifAuthority {
            tickToConfigs.forEach { (tick, configs) ->
                launch {
                    while (true) {
                        sendQueue.add {
                            val node = thisNode.get() ?: run {
                                this.cancel()
                                return@add
                            }
                            configs
                                    .filterValues { syncConfig -> syncConfig.shouldSendUpdate() }
                                    .forEach { (fqName, syncConfig) ->
                                        withRemoteListeners { peerId ->
                                            val syncData = syncConfig.serializeSyncData()

                                            debug { "Synchronizer[${this@ifAuthority.name}]: sending sync data: $syncData for property: $fqName to peer with id: $peerId" }

                                            node.rpcId(peerId, thisNodeAsType<Synchronized>()::replicateForSynchronized, fqName, syncData)
//                                            when (syncConfig.syncMethod) {
//                                                SyncConfig.SyncMethod.RELIABLE -> node.rpcId(peerId, thisNodeAsType<Synchronized>()::replicateForSynchronized, fqName, syncData)
//                                                SyncConfig.SyncMethod.UNRELIABLE -> node.rpcUnreliableId(peerId, thisNodeAsType<Synchronized>()::replicateForSynchronized, fqName, syncData)
//                                            }
                                        }
                                    }
                        }
                        delay(tick)
                    }
//                    ticker(tick).consumeAsFlow().collectLatest {
//                        withContext(mainDispatcher()) {
//                            val node = thisNode.get() ?: run {
//                                this.cancel()
//                                return@withContext
//                            }
//                            configs.forEach { (fqName, syncConfig) ->
//                                withRemoteListeners { peerId ->
//                                    val syncData = syncConfig.serializeSyncData()
//                                    debug { "Synchronizer[${this@ifAuthority.name}]: sending sync data: $syncData for property: $fqName to peer with id: $peerId" }
//
//                                    when (syncConfig.syncMethod) {
//                                        SyncConfig.SyncMethod.RELIABLE -> node.rpcId(peerId, thisNodeAsType<Synchronized>()::replicateForSynchronized, fqName, syncData)
//                                        SyncConfig.SyncMethod.UNRELIABLE -> node.rpcUnreliableId(peerId, thisNodeAsType<Synchronized>()::replicateForSynchronized, fqName, syncData)
//                                    }
//                                }
//                            }
//                        }
//                    }
                }
            }
        }
    }

    override fun replicateForSynchronized(fqName: String, data: SerializedData) {
        receiveQueue.add {
            ifPeer {
                debug { "Synchronizer[${this.name}]: received sync data: $data for property: $fqName" }
                syncConfig[fqName]?.applySyncData(data)
            }
        }
    }
}
