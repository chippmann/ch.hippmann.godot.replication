package ch.hippmann.godot.replication

import ch.hippmann.godot.utilities.coroutines.scope.DefaultGodotCoroutineScope
import ch.hippmann.godot.utilities.coroutines.scope.GodotCoroutineScope
import ch.hippmann.godot.utilities.logging.debug
import godot.Node
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.reflect.KFunction2

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
                                        val syncData = syncConfig.serializeSyncData()

                                        debug { "Synchronizer[${this@ifAuthority.name}]: sending sync data: $syncData for property: $fqName to peers" }

                                        val rpcFunction: KFunction2<String, String, Unit> = when(syncConfig.syncMethod) {
                                            SyncConfig.SyncMethod.RELIABLE -> thisNodeAsType<Synchronized>()::replicateForSynchronizedReliable
                                            SyncConfig.SyncMethod.UNRELIABLE -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliable
                                            SyncConfig.SyncMethod.UNRELIABLE_ORDERED -> when(syncConfig.syncChannel) {
                                                SyncConfig.SyncChannel.CHANNEL_0 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel0
                                                SyncConfig.SyncChannel.CHANNEL_1 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel1
                                                SyncConfig.SyncChannel.CHANNEL_2 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel2
                                                SyncConfig.SyncChannel.CHANNEL_3 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel3
                                                SyncConfig.SyncChannel.CHANNEL_4 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel4
                                                SyncConfig.SyncChannel.CHANNEL_5 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel5
                                                SyncConfig.SyncChannel.CHANNEL_6 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel6
                                                SyncConfig.SyncChannel.CHANNEL_7 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel7
                                                SyncConfig.SyncChannel.CHANNEL_8 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel8
                                                SyncConfig.SyncChannel.CHANNEL_9 -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliableOrderedChannel9
                                            }
                                        }

                                        withRemoteListeners { peerId: Long ->
                                            node.rpcId(peerId, rpcFunction, fqName, syncData)
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

    private fun replicate(fqName: String, data: SerializedData) {
        receiveQueue.add {
            ifPeer {
                debug { "Synchronizer[${this.name}]: received sync data: $data for property: $fqName" }
                syncConfig[fqName]?.applySyncData(data)
            }
        }
    }

    override fun replicateForSynchronizedReliable(fqName: String, data: SerializedData) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliable(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel0(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel1(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel2(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel3(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel4(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel5(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel6(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel7(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel8(fqName: String, data: String) = this.replicate(fqName, data)
    override fun replicateForSynchronizedUnreliableOrderedChannel9(fqName: String, data: String) = this.replicate(fqName, data)
}
