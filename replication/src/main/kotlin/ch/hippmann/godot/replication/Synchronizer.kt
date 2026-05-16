package ch.hippmann.godot.replication

import ch.hippmann.godot.utilities.logging.Log
import godot.api.Node
import godot.core.connect
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction2

class Synchronizer : Synchronized, WithRemoteListeners by RemoteListenerManager(),
    WithNodeAccess by WithNodeAccessDelegate(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Default + SupervisorJob() + object : CoroutineExceptionHandler {
        override fun handleException(context: CoroutineContext, exception: Throwable) {
            Log.err("An error occurred in a coroutine in ${this@Synchronizer::class.qualifiedName}", exception)
        }

        override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler
    }

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

    // ConcurrentLinkedQueue is required because the ticker coroutines run on
    // Dispatchers.Default and produce send-side entries while performSynchronization
    // drains both queues from Godot's main thread.
    private val sendQueue: Queue<() -> Unit> = ConcurrentLinkedQueue()
    private val receiveQueue: Queue<() -> Unit> = ConcurrentLinkedQueue()

    override fun <T> T.initSynchronization() where T : Node, T : Synchronized {
        initNodeAccess()
        // On peer subscribe, push the current value of every synced property to the
        // newly-subscribed peer specifically. Without this catch-up, a peer whose
        // handshake completes AFTER a property has settled never sees that property —
        // the per-property `shouldSendUpdate` dedup is global to the property, not
        // per-peer, so subsequent ticks skip the send. Replicator already does the
        // analogous thing for managed children via peerSpawnAllForReplicated.
        initListening(onPeerSubscribed = { peerId -> sendFullStateTo(peerId) })
        this.treeExiting.connect {
            // cancel all syncs when exiting tree
            coroutineContext.cancelChildren()
        }

        // the delegate (this class) cannot access properties overridden by the implementer. So we cannot get its
        // config. Thus, we manually assign it here to whatever the implementer defined
        this@Synchronizer.syncConfig = this.syncConfig

        this.ready.connect(this, Synchronized::notificationOnReadyForSynchronized)
        Log.debug { "Synchronizer[${this.name}]: initialised" }
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
                    while (isActive) {
                        sendQueue.add {
                            val node = thisNode.get() ?: run {
                                // `this` here is the Synchronizer's CoroutineScope, NOT just
                                // the launching coroutine — so this cancels EVERY tick-group
                                // launch, intentional once the host node is gone (we have
                                // nothing left to sync) but worth being explicit about.
                                this.cancel()
                                return@add
                            }
                            configs
                                .filterValues { syncConfig -> syncConfig.shouldSendUpdate() }
                                .forEach { (fqName, syncConfig) ->
                                    val syncData = syncConfig.serializeSyncData()
                                    Log.debug { "Synchronizer[${this@ifAuthority.name}]: sending sync data: $syncData for property: $fqName to peers" }
                                    val rpcFunction = rpcFunctionFor(syncConfig)
                                    withRemoteListeners { peerId: Long ->
                                        node.rpcId(peerId, rpcFunction, fqName, syncData)
                                    }
                                }
                        }
                        delay(tick)
                    }
                }
            }
        }
    }

    /**
     * Push the current value of every synced property to [peerId]. Called from the
     * onPeerSubscribed hook so newly-joined peers catch up to whatever the authority
     * has already settled — works around the per-property `shouldSendUpdate` dedup
     * being global rather than per-peer.
     */
    private fun sendFullStateTo(peerId: Long) {
        ifAuthority {
            val node = thisNode.get() ?: return@ifAuthority
            syncConfig.forEach { (fqName, syncConfig) ->
                val syncData = syncConfig.serializeSyncData()
                val rpcFunction = rpcFunctionFor(syncConfig)
                Log.debug { "Synchronizer[${node.name}]: catch-up sync of '$fqName' to peer $peerId" }
                node.rpcId(peerId, rpcFunction, fqName, syncData)
            }
        }
    }

    private fun rpcFunctionFor(syncConfig: SyncConfig): KFunction2<String, String, Unit> =
        when (syncConfig.syncMethod) {
            SyncConfig.SyncMethod.RELIABLE -> thisNodeAsType<Synchronized>()::replicateForSynchronizedReliable
            SyncConfig.SyncMethod.UNRELIABLE -> thisNodeAsType<Synchronized>()::replicateForSynchronizedUnreliable
            SyncConfig.SyncMethod.UNRELIABLE_ORDERED -> when (syncConfig.syncChannel) {
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

    private fun replicate(fqName: String, data: SerializedData) {
        // Authority verification has to happen synchronously inside the @Rpc handler:
        // multiplayer.getRemoteSenderId() is only valid while the RPC is being
        // dispatched, not later in the queued continuation.
        val node = thisNode.get() ?: return
        val multiplayer = node.multiplayer ?: return
        val senderId = multiplayer.getRemoteSenderId()
        val authorityId = node.getMultiplayerAuthority()
        if (senderId != authorityId) {
            Log.warn { "Synchronizer[${node.name}]: rejecting sync RPC for '$fqName' from peer $senderId (authority is $authorityId)" }
            return
        }
        receiveQueue.add {
            ifPeer {
                Log.debug { "Synchronizer[${this.name}]: received sync data: $data for property: $fqName" }
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
