package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.core.session.Epoch
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.Membership
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.session.SessionId

data class SessionView(
    val sessionId: SessionId,
    val localPlayerId: PlayerId,
    val master: PlayerId,
    val epoch: Epoch,
    val members: List<MemberRecord>,
    /** Members with an established link, plus the local player; Godot RPCs only reach these. */
    val connected: Set<PlayerId>,
) {
    val isMaster: Boolean
        get() = localPlayerId == master

    val memberIds: Set<PlayerId>
        get() = members.map { member -> member.id }.toSet()

    companion object {
        internal fun of(localPlayerId: PlayerId, membership: Membership, connected: Set<PlayerId>): SessionView = SessionView(
            sessionId = membership.sessionId,
            localPlayerId = localPlayerId,
            master = membership.master,
            epoch = membership.epoch,
            members = membership.members.values.sortedBy { member -> member.id.value },
            connected = connected + localPlayerId,
        )
    }
}
