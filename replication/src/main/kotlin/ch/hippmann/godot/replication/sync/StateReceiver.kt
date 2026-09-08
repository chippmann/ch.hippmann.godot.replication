package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.replication.DeltaReader
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.transport.Transport
import ch.hippmann.godot.replication.transport.TransportLog
import ch.hippmann.godot.replication.diagnostics.NetworkStatistics

internal class StateReceiver(private val transport: Transport) {
    var droppedEntries: Int = 0
        private set
    private val clockOffsets = HashMap<Int, Long>()

    fun onPacket(sender: PlayerId, bytes: ByteArray) {
        val reader = DeltaReader.parse(bytes)
        val receiveTime = senderTime(sender, reader.sendTimeMilliseconds, FrameClock.nowMilliseconds)
        reader.forEachEntry { entry ->
            val replica = NodeRegistry.byNetworkId(entry.networkId)
            if (replica == null || replica.owner != sender) {
                droppedEntries++
                transport.counters.add(NetworkStatistics.DROPPED_ENTRIES)
                TransportLog.log { "dropping ${reader.type} entry for ${entry.networkId.value} from ${sender.value}: ${if (replica == null) "unknown node" else "not the owner"}" }
                return@forEachEntry
            }
            replica.readValues(entry.values, entry.mask, receiveTime)
        }
    }

    fun forgetPeer(player: PlayerId) {
        clockOffsets.remove(player.value)
    }

    /**
     * Samples are stamped in the sender's clock mapped onto ours, so a stall on this side does not turn into fake gaps and
     * the interpolation keeps the sender's spacing. The smallest transit seen defines the mapping and may creep up slowly.
     */
    private fun senderTime(sender: PlayerId, sendTimeMilliseconds: Long, receiveTimeMilliseconds: Long): Long {
        val observed = receiveTimeMilliseconds - sendTimeMilliseconds
        val previous = clockOffsets[sender.value]
        val offset = if (previous == null) observed else minOf(observed, previous + CLOCK_DRIFT_ALLOWANCE_MILLISECONDS)
        clockOffsets[sender.value] = offset
        return sendTimeMilliseconds + offset
    }

    private companion object {
        const val CLOCK_DRIFT_ALLOWANCE_MILLISECONDS = 1L
    }
}
