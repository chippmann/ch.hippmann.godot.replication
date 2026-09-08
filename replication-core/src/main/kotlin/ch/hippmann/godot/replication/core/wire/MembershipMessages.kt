package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.session.Epoch
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class MemberJoined(val epoch: Epoch, val member: MemberRecord) : WireMessage {
    override val type: MessageType get() = MessageType.MEMBER_JOINED
}

@Serializable
public enum class LeaveReason {
    GRACEFUL,
    TIMEOUT,
    KICKED,
    INCONSISTENT,
}

@Serializable
public data class Leave(val reason: LeaveReason) : WireMessage {
    override val type: MessageType get() = MessageType.LEAVE
}

@Serializable
public data class MembershipUpdate(
    val epoch: Epoch,
    val members: List<MemberRecord>,
    val nextJoinSequence: Int,
) : WireMessage {
    override val type: MessageType get() = MessageType.MEMBERSHIP_UPDATE
}

@Serializable
public data class Kick(val target: PlayerId, val reason: String) : WireMessage {
    override val type: MessageType get() = MessageType.KICK
}
