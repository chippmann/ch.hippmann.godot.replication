package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.replication.DeltaReader
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.transport.Transport
import ch.hippmann.godot.replication.transport.TransportLog
import ch.hippmann.godot.replication.diagnostics.NetworkStatistics

internal class StateReceiver(private val transport: Transport) {
    var droppedEntries: Int = 0
        private set

    fun onPacket(sender: PlayerId, bytes: ByteArray) {
        val reader = DeltaReader.parse(bytes)
        val receiveTime = FrameClock.nowMilliseconds
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
}
