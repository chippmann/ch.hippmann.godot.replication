package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.Hello
import ch.hippmann.godot.replication.core.wire.HelloAccepted
import ch.hippmann.godot.replication.core.wire.JoinProof
import ch.hippmann.godot.replication.core.wire.JoinRequest
import ch.hippmann.godot.replication.core.wire.Redirect
import ch.hippmann.godot.replication.core.wire.RejectReason
import ch.hippmann.godot.replication.core.wire.Rejected
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.transport.EnetLink
import godot.coroutines.launch
import kotlinx.coroutines.delay

/** Member side of inbound links: joiners looking for the master, and admitted joiners meshing with everyone else. */
internal class HandshakeHandler(private val runtime: SessionRuntime) {
    private val unidentified = HashMap<Long, EnetLink>()
    private val pendingHellos = HashMap<Int, EnetLink>()

    fun onInboundLink(link: EnetLink) {
        unidentified[link.key] = link
        runtime.manager.launch {
            delay(UNIDENTIFIED_TIMEOUT_MILLISECONDS)
            if (unidentified.remove(link.key) != null && link.player == null) {
                runtime.transport.close(link, graceful = true)
            }
        }
    }

    fun onLinkClosed(link: EnetLink) {
        unidentified.remove(link.key)
        pendingHellos.values.removeIf { pending -> pending === link }
        runtime.masterRole?.onLinkClosed(link)
    }

    fun onMessage(link: EnetLink, message: WireMessage): Boolean {
        when (message) {
            is JoinRequest -> if (runtime.isMaster) runtime.masterRole?.onJoinRequest(link, message) else redirect(link)
            is JoinProof -> if (runtime.isMaster) runtime.masterRole?.onJoinProof(link, message) else redirect(link)
            is Hello -> onHello(link, message)
            else -> return false
        }
        return true
    }

    fun onMemberJoined(member: MemberRecord) {
        pendingHellos.remove(member.id.value)?.let { link -> accept(link, member.id) }
    }

    private fun redirect(link: EnetLink) {
        val membership = runtime.membership
        val master = membership?.members?.get(membership.master)
        if (master == null) {
            link.sendMessage(Rejected(RejectReason.NOT_MEMBER))
        } else {
            link.sendMessage(Redirect(master.endpoints))
        }
        runtime.transport.close(link, graceful = true)
    }

    private fun onHello(link: EnetLink, hello: Hello) {
        val membership = runtime.membership
        if (membership == null || hello.sessionId != membership.sessionId || hello.playerId == runtime.localPlayerId) {
            link.sendMessage(Rejected(RejectReason.NOT_MEMBER))
            runtime.transport.close(link, graceful = true)
            return
        }
        if (membership.contains(hello.playerId)) {
            accept(link, hello.playerId)
            return
        }
        // The master's MemberJoined for this player is still on its way; it normally races the Hello.
        pendingHellos[hello.playerId.value] = link
        runtime.manager.launch {
            delay(PENDING_HELLO_TIMEOUT_MILLISECONDS)
            if (pendingHellos.remove(hello.playerId.value) === link) {
                link.sendMessage(Rejected(RejectReason.NOT_MEMBER))
                runtime.transport.close(link, graceful = true)
            }
        }
    }

    private fun accept(link: EnetLink, player: PlayerId) {
        unidentified.remove(link.key)
        val membership = runtime.membership ?: return
        runtime.transport.identify(link, player)
        link.sendMessage(HelloAccepted(runtime.localPlayerId, membership.epoch))
        runtime.meshPeer.announcePeer(player)
        runtime.publishSession()
    }

    private companion object {
        const val UNIDENTIFIED_TIMEOUT_MILLISECONDS = 10_000L
        const val PENDING_HELLO_TIMEOUT_MILLISECONDS = 5_000L
    }
}
