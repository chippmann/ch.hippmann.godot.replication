package ch.hippmann.godot.replication

import ch.hippmann.godot.utilities.logging.Log
import godot.api.Node
import kotlin.reflect.KFunction2

class Synchronizer : Synchronized, WithRemoteListeners by RemoteListenerManager(),
    WithNodeAccess by WithNodeAccessDelegate() {

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
                        .associate { entry -> entry.second }
                        .filterValues { config -> config.syncOnTick }
                }
        }

    // For each tick-group (keyed by tick interval in ms), the wall-clock time at which
    // the group should next fire. Populated lazily on the first performSynchronization
    // call after the node is ready. Drift-correcting: a long pause skips intermediate
    // ticks rather than firing them all in a burst.
    private val nextTickTimeMs: MutableMap<Long, Long> = mutableMapOf()

    override fun <T> T.initSynchronization() where T : Node, T : Synchronized {
        initNodeAccess()
        // On peer subscribe, push the current value of every synced property to the
        // newly-subscribed peer specifically. Without this catch-up, a peer whose
        // handshake completes AFTER a property has settled never sees that property —
        // the per-property `shouldSendUpdate` dedup is global to the property, not
        // per-peer, so subsequent ticks skip the send. Replicator already does the
        // analogous thing for managed children via peerSpawnAllForReplicated.
        initListening(onPeerSubscribed = { peerId -> sendFullStateTo(peerId) })

        // the delegate (this class) cannot access properties overridden by the implementer.
        // So we cannot get its config. Thus, we manually assign it here to whatever the
        // implementer defined.
        this@Synchronizer.syncConfig = this.syncConfig

        Log.debug { "Synchronizer[${this.name}]: initialised" }
    }

    /**
     * Heart-beat called by the consumer from `_process`. Advances each tick group's
     * accumulator, fires the group's RPC send when its interval has elapsed. Drains
     * any queued sync state changes (none under the current main-thread design — kept
     * for API compatibility / future use).
     *
     * Runs entirely on Godot's main thread; no `Dispatchers.Default` coroutines, no
     * cross-thread queues. Eliminates the safepoint-deadlock-at-shutdown hazard the
     * previous coroutine-based ticker exhibited (bug #20). Tick timing is
     * accumulator-based and self-correcting — replaces the old `delay(tick)` drift
     * (bug #8).
     */
    override fun performSynchronization() {
        ifAuthority {
            val nowMs = System.nanoTime() / 1_000_000L
            tickToConfigs.forEach { (tickMs, configs) ->
                val scheduled = nextTickTimeMs[tickMs] ?: (nowMs + tickMs).also { nextTickTimeMs[tickMs] = it }
                if (nowMs >= scheduled) {
                    nextTickTimeMs[tickMs] = nowMs + tickMs
                    fireTickGroup(configs)
                }
            }
        }
    }

    private fun Node.fireTickGroup(configs: SyncConfigs) {
        configs
            .filterValues { syncConfig -> syncConfig.shouldSendUpdate() }
            .forEach { (fqName, syncConfig) ->
                val syncData = syncConfig.serializeSyncData()
                Log.debug { "Synchronizer[${this.name}]: sending sync data: $syncData for property: $fqName to peers" }
                val rpcFunction = rpcFunctionFor(syncConfig)
                withRemoteListeners { peerId: Long ->
                    rpcId(peerId, rpcFunction, fqName, syncData)
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

    // notificationOnReadyForSynchronized used to start the ticker coroutines under the
    // old design. With main-thread accumulator timing nothing is needed here; the
    // method survives only because the interface requires the @RegisterFunction entry.
    override fun notificationOnReadyForSynchronized() {
        // intentional no-op — see performSynchronization()
    }

    private fun replicate(fqName: String, data: SerializedData) {
        // Authority verification has to happen synchronously inside the @Rpc handler:
        // multiplayer.getRemoteSenderId() is only valid while the RPC is being
        // dispatched, not later. Also: Godot dispatches RPC handlers on its main
        // thread, so applying the value is safe to do directly — no queue needed.
        val node = thisNode.get() ?: return
        val multiplayer = node.multiplayer ?: return
        val senderId = multiplayer.getRemoteSenderId()
        val authorityId = node.getMultiplayerAuthority()
        if (senderId != authorityId) {
            Log.warn { "Synchronizer[${node.name}]: rejecting sync RPC for '$fqName' from peer $senderId (authority is $authorityId)" }
            return
        }
        ifPeer {
            Log.debug { "Synchronizer[${this.name}]: received sync data: $data for property: $fqName" }
            syncConfig[fqName]?.applySyncData(data)
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
