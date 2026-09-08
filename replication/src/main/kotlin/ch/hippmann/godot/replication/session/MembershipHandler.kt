package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.Leave
import ch.hippmann.godot.replication.core.wire.LeaveReason
import ch.hippmann.godot.replication.core.wire.MemberJoined
import ch.hippmann.godot.replication.core.wire.MembershipUpdate
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.transport.EnetLink

/** Keeps the membership view in step with the master, the leavers and the link losses; elects on master loss. */
internal class MembershipHandler(private val runtime: SessionRuntime) {

    fun onMessage(link: EnetLink, message: WireMessage): Boolean {
        val sender = link.player ?: return false
        when (message) {
            is MemberJoined -> onMemberJoined(sender, message)
            // The link stays announced until ENet reports it closed: the leaver's last RPC packets travel on
            // another channel and may still be in flight behind this message.
            is Leave -> {
                link.leaving = true
                removeMember(sender, message.reason)
            }
            is MembershipUpdate -> onMembershipUpdate(sender, message)
            else -> return false
        }
        return true
    }

    fun onLinkClosed(link: EnetLink) {
        val player = link.player ?: return
        removeMember(player, LeaveReason.TIMEOUT)
        runtime.meshPeer.announcePeerLeft(player)
        runtime.publishSession()
    }

    private fun onMemberJoined(sender: PlayerId, message: MemberJoined) {
        val membership = runtime.membership ?: return
        if (sender != membership.master || message.epoch.value < membership.epoch.value) return
        if (membership.contains(message.member.id)) return
        runtime.membership = membership.with(message.member).copy(
            nextJoinSequence = maxOf(membership.nextJoinSequence, message.member.id.value + 1),
        )
        runtime.onMemberAdded(message.member)
        runtime.handshake.onMemberJoined(message.member)
    }

    private fun onMembershipUpdate(sender: PlayerId, message: MembershipUpdate) {
        val membership = runtime.membership ?: return
        if (message.epoch.value < membership.epoch.value) return
        val updated = membership.copy(
            epoch = message.epoch,
            members = message.members.associateBy { member -> member.id },
            nextJoinSequence = message.nextJoinSequence,
        )
        if (sender != updated.master) return
        runtime.membership = updated
        runtime.publishSession()
    }

    fun removeMember(player: PlayerId, reason: LeaveReason) {
        val membership = runtime.membership ?: return
        if (!membership.contains(player)) return
        val previousMaster = membership.master
        var updated = membership.without(player)
        val masterLost = player == previousMaster
        if (masterLost) updated = updated.withEpoch(membership.epoch.next())
        runtime.membership = updated
        if (masterLost && updated.master == runtime.localPlayerId) becomeMaster()

        runtime.replication.onMemberLeft(player)
        if (masterLost) {
            runtime.replication.onMasterChanged()
            runtime.levelService.onMasterChanged()
        }
        // State first, events second: an event handler must already see the new session view.
        runtime.publishSession()
        runtime.emit(NetworkEvent.MemberLeft(player, reason))
        if (masterLost) runtime.emit(NetworkEvent.MasterChanged(updated.master, updated.epoch))
    }

    private fun becomeMaster() {
        val membership = runtime.membership ?: return
        val role = MasterRole(runtime, runtime.passwordVerifier)
        role.admissionFrozenUntil = nowMilliseconds() + ADMISSION_FREEZE_MILLISECONDS
        runtime.masterRole = role
        runtime.transport.broadcastMessage(MembershipUpdate(membership.epoch, membership.members.values.toList(), membership.nextJoinSequence))
    }

    private companion object {
        const val ADMISSION_FREEZE_MILLISECONDS = 2_000L
    }
}
