package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.PasswordChallenge
import ch.hippmann.godot.replication.core.session.PasswordVerifier
import ch.hippmann.godot.replication.core.wire.Admitted
import ch.hippmann.godot.replication.core.wire.Challenge
import ch.hippmann.godot.replication.core.wire.JoinProof
import ch.hippmann.godot.replication.core.wire.JoinRequest
import ch.hippmann.godot.replication.core.wire.MemberJoined
import ch.hippmann.godot.replication.core.wire.PROTOCOL_VERSION
import ch.hippmann.godot.replication.core.wire.RejectReason
import ch.hippmann.godot.replication.core.wire.Rejected
import ch.hippmann.godot.replication.transport.EnetLink

/** Admission and player id allocation; only the current master holds an instance. */
internal class MasterRole(
    private val runtime: SessionRuntime,
    private val passwordVerifier: PasswordVerifier?,
) {
    private class PendingJoin(val request: JoinRequest, val nonce: ByteArray)

    private val pending = HashMap<Long, PendingJoin>()
    private val failedAttempts = HashMap<String, ArrayDeque<Long>>()

    var admissionFrozenUntil: Long = 0

    fun onJoinRequest(link: EnetLink, request: JoinRequest) {
        val membership = runtime.membership ?: return
        val lobby = runtime.lobbyConfiguration
        val reason = when {
            request.protocolVersion != PROTOCOL_VERSION -> RejectReason.PROTOCOL_MISMATCH
            membership.members.size >= lobby.maximumPlayers -> RejectReason.FULL
            lobby.locked -> RejectReason.LOCKED
            nowMilliseconds() < admissionFrozenUntil -> RejectReason.RETRY
            isLockedOut(link.remoteAddress) -> RejectReason.RETRY
            else -> null
        }
        if (reason != null) {
            reject(link, reason)
            return
        }
        val nonce = PasswordChallenge.randomBytes(PasswordChallenge.NONCE_SIZE)
        pending[link.key] = PendingJoin(request, nonce)
        link.sendMessage(Challenge(membership.sessionId, passwordVerifier?.salt ?: ByteArray(0), nonce, passwordVerifier != null))
    }

    fun onJoinProof(link: EnetLink, proof: JoinProof) {
        val join = pending.remove(link.key)
        if (join == null) {
            reject(link, RejectReason.NOT_MEMBER)
            return
        }
        val verifier = passwordVerifier
        if (verifier != null && !verifier.matches(join.nonce, proof.proof)) {
            recordFailure(link.remoteAddress)
            reject(link, RejectReason.WRONG_PASSWORD)
            return
        }
        admit(link, join.request)
    }

    fun onLinkClosed(link: EnetLink) {
        pending.remove(link.key)
    }

    private fun admit(link: EnetLink, request: JoinRequest) {
        val membership = runtime.membership ?: return
        val (id, allocated) = membership.allocateNext()
        val endpoints = listOf(Endpoint(link.remoteAddress, request.listenPort)) +
            request.localAddresses.map { address -> Endpoint(address, request.listenPort) }
        val member = MemberRecord(id, request.profile, endpoints.distinct())
        val updated = allocated.with(member)
        runtime.membership = updated
        runtime.transport.identify(link, id)
        link.sendMessage(
            Admitted(
                playerId = id,
                sessionId = updated.sessionId,
                epoch = updated.epoch,
                nextJoinSequence = updated.nextJoinSequence,
                members = updated.members.values.toList(),
                lobby = runtime.lobbyConfiguration.withoutPassword(),
                level = runtime.levelState,
                password = passwordVerifier,
            ),
        )
        runtime.transport.broadcastMessage(MemberJoined(updated.epoch, member), except = id)
        runtime.meshPeer.announcePeer(id)
        runtime.onMemberAdded(member)
    }

    private fun reject(link: EnetLink, reason: RejectReason) {
        runtime.emit(NetworkEvent.JoinAttemptRejected(link.remoteAddress, reason))
        link.sendMessage(Rejected(reason))
        runtime.transport.close(link, graceful = true)
    }

    private fun recordFailure(address: String) {
        val attempts = failedAttempts.getOrPut(address) { ArrayDeque() }
        attempts.addLast(nowMilliseconds())
        while (attempts.size > MAXIMUM_FAILURES) attempts.removeFirst()
    }

    private fun isLockedOut(address: String): Boolean {
        val attempts = failedAttempts[address] ?: return false
        val now = nowMilliseconds()
        attempts.removeAll { timestamp -> now - timestamp > LOCKOUT_MILLISECONDS }
        return attempts.size >= MAXIMUM_FAILURES
    }

    private companion object {
        const val MAXIMUM_FAILURES = 3
        const val LOCKOUT_MILLISECONDS = 60_000L
    }
}
