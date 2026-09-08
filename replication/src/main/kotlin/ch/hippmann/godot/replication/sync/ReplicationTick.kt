package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.replication.ClassSchema
import ch.hippmann.godot.replication.diagnostics.NetworkStatistics
import ch.hippmann.godot.replication.core.replication.DeltaWriter
import ch.hippmann.godot.replication.core.replication.PeerSendState
import ch.hippmann.godot.replication.core.replication.SyncMode
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.transport.Channels
import ch.hippmann.godot.replication.transport.Transport
import ch.hippmann.godot.replication.transport.TransportFlags
import ch.hippmann.godot.replication.transport.TransportLog

/** Owner side: one unreliable delta and, when needed, one reliable packet per tick for all peers, full states for newcomers. */
internal class ReplicationTick(private val transport: Transport, private val tickRate: Int) {
    private val peerSendState = PeerSendState()
    var tick: Int = 0
        private set

    fun run(connectedPeers: Set<PlayerId>) {
        tick++
        NodeRegistry.currentTick = tick
        transport.counters.add(NetworkStatistics.TICKS)
        val peers = connectedPeers - NodeRegistry.localPlayerId
        val shared = PacketPair(tick)
        val perPeer = HashMap<PlayerId, PacketPair>()

        for (replica in NodeRegistry.active) {
            if (!replica.isOwnedLocally) continue
            val networkId = replica.networkId ?: continue
            val schema = replica.schema ?: continue
            val interested = peers.filter { peer -> replica.interestFilter.includes(replica.node, peer) }
            logInterestChanges(networkId, interested)
            syncFullState(replica, peers, interested)
            val mask = collectMask(replica)
            if (mask == 0L) continue
            val propertyFilters = replica.properties.any { property -> property.options.interestFilter != null }
            if (interested.size == peers.size && !propertyFilters) {
                shared.write(replica, networkId, schema, mask)
            } else {
                for (peer in interested) {
                    val peerMask = mask and replica.properties.fold(0L) { excluded, property ->
                        val filter = property.options.interestFilter
                        if (filter != null && !filter.includes(replica.node, peer)) excluded or (1L shl property.index) else excluded
                    }.inv()
                    if (peerMask != 0L) perPeer.getOrPut(peer) { PacketPair(tick) }.write(replica, networkId, schema, peerMask)
                }
            }
            replica.dirtyMask = 0
        }
        shared.send { channel, packet, flags -> transport.broadcast(channel, packet, flags) }
        for ((peer, pair) in perPeer) pair.send { channel, packet, flags -> transport.send(peer, channel, packet, flags) }
    }

    private inner class PacketPair(tick: Int) {
        private val delta = DeltaWriter(MessageType.STATE_DELTA, tick, FrameClock.nowMilliseconds)
        private val reliable = DeltaWriter(MessageType.STATE_RELIABLE, tick, FrameClock.nowMilliseconds)

        fun write(replica: Replica, networkId: NetworkId, schema: ClassSchema, mask: Long) {
            val reliableMask = mask and schema.reliableMask
            val unreliableMask = mask and schema.reliableMask.inv()
            if (reliableMask != 0L) reliable.entry(networkId, reliableMask) { writer -> replica.writeValues(writer, reliableMask) }
            if (unreliableMask != 0L) delta.entry(networkId, unreliableMask) { writer -> replica.writeValues(writer, unreliableMask) }
        }

        fun send(sender: (channel: Int, packet: ByteArray, flags: Int) -> Unit) {
            for (packet in reliable.finish()) {
                transport.counters.add(NetworkStatistics.RELIABLE_STATE_PACKETS_OUT)
                sender(Channels.CONTROL, packet, TransportFlags.RELIABLE)
            }
            for (packet in delta.finish()) {
                transport.counters.add(NetworkStatistics.STATE_PACKETS_OUT)
                sender(Channels.STATE, packet, TransportFlags.SEQUENCED)
            }
        }
    }

    fun forgetPeer(player: PlayerId): Unit = peerSendState.forgetPeer(player)

    private val lastInterested = HashMap<Long, Set<PlayerId>>()

    private fun logInterestChanges(networkId: NetworkId, interested: List<PlayerId>) {
        if (!TransportLog.enabled) return
        val current = interested.toSet()
        if (lastInterested.put(networkId.value, current) != current) {
            TransportLog.log { "interest in node ${networkId.value}: now ${current.map { it.value }}" }
        }
    }

    fun forgetNode(networkId: NetworkId): Unit = peerSendState.forgetNode(networkId)

    private fun collectMask(replica: Replica): Long {
        var mask = replica.dirtyMask
        for (property in replica.properties) {
            val bit = 1L shl property.index
            if (property.pollChanged()) {
                mask = mask or bit
                property.lastChangedTick = tick
            }
            val mode = property.options.mode
            if (mode is SyncMode.Continuous) {
                val dueThisTick = mode.rate <= 0 || tick % maxOf(1, tickRate / mode.rate) == 0
                val recentlyChanged = tick - property.lastChangedTick <= mode.idleAfterTicks
                if (dueThisTick && recentlyChanged) mask = mask or bit
                if (!dueThisTick) mask = mask and bit.inv()
            }
        }
        return mask
    }

    /** Peers that just connected or just came into interest get everything reliably; peers leaving interest are forgotten. */
    private fun syncFullState(replica: Replica, peers: Set<PlayerId>, interested: List<PlayerId>) {
        val networkId = replica.networkId ?: return
        val schema = replica.schema ?: return
        for (peer in peers) {
            if (peer !in interested) {
                peerSendState.forget(peer, networkId)
                continue
            }
            if (peerSendState.knows(peer, networkId)) continue
            TransportLog.log { "full state of node ${networkId.value} to ${peer.value} at tick $tick" }
            val full = DeltaWriter(MessageType.STATE_FULL, tick, FrameClock.nowMilliseconds)
            full.entry(networkId, schema.fullMask) { writer: ByteWriter -> replica.writeValues(writer, schema.fullMask) }
            for (packet in full.finish()) transport.send(peer, Channels.CONTROL, packet, TransportFlags.RELIABLE)
            peerSendState.markKnown(peer, networkId)
        }
    }
}
