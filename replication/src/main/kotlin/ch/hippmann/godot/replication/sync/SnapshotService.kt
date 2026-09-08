package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.replication.DeltaWriter
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.core.wire.SnapshotDone
import ch.hippmann.godot.replication.core.wire.SnapshotRequest
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.session.SessionRuntime
import ch.hippmann.godot.replication.transport.Channels
import ch.hippmann.godot.replication.transport.TransportFlags
import ch.hippmann.godot.replication.transport.TransportLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** A late joiner asks every member for the nodes it owns; each answers with spawns, full states and a done marker. */
internal class SnapshotService(private val session: SessionRuntime) {
    private val pendingDone = HashMap<Int, CompletableDeferred<Unit>>()

    suspend fun request(timeoutPerMemberMilliseconds: Long): Set<PlayerId> {
        val members = session.transport.connectedPlayers - session.localPlayerId
        for (member in members) pendingDone[member.value] = CompletableDeferred()
        session.transport.broadcastMessage(SnapshotRequest(session.levelState.sequence))
        val missing = HashSet<PlayerId>()
        for (member in members) {
            val done = withTimeoutOrNull(timeoutPerMemberMilliseconds) { pendingDone[member.value]?.await() }
            if (done == null) missing += member
            pendingDone.remove(member.value)
        }
        return missing
    }

    fun onMessage(sender: PlayerId, message: WireMessage): Boolean {
        when (message) {
            is SnapshotRequest -> send(sender)
            is SnapshotDone -> pendingDone[sender.value]?.complete(Unit)
            else -> return false
        }
        return true
    }

    private fun send(to: PlayerId) {
        var count = 0
        val owned = NodeRegistry.active.filter { replica -> replica.isOwnedLocally }.sortedBy { replica -> replica.node.getPath().path.count { it == '/' } }
        for (replica in owned) {
            val networkId = replica.networkId ?: continue
            val schema = replica.schema ?: continue
            if (replica.spawnRecord != null) {
                TransportLog.log { "snapshot spawn of node ${networkId.value} to ${to.value}" }
                session.transport.send(to, Channels.CONTROL, session.replication.spawnService.spawnMessage(replica).encode(), TransportFlags.RELIABLE)
            } else {
                TransportLog.log { "snapshot full state of node ${networkId.value} to ${to.value}" }
                val full = DeltaWriter(MessageType.STATE_FULL, 0, FrameClock.nowMilliseconds)
                full.entry(networkId, schema.fullMask) { writer: ByteWriter -> replica.writeValues(writer, schema.fullMask) }
                for (packet in full.finish()) session.transport.send(to, Channels.CONTROL, packet, TransportFlags.RELIABLE)
            }
            count++
        }
        TransportLog.log { "snapshot of $count nodes sent to ${to.value}" }
        session.transport.sendMessage(to, SnapshotDone(session.levelState.sequence, count))
    }
}
