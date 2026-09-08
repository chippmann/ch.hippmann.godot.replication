package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.core.session.Epoch
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.LeaveReason
import ch.hippmann.godot.replication.core.wire.RejectReason
import godot.api.Node

sealed interface NetworkEvent {
    data class MemberJoined(val member: MemberRecord) : NetworkEvent

    data class MemberLeft(val player: PlayerId, val reason: LeaveReason) : NetworkEvent

    data class MasterChanged(val master: PlayerId, val epoch: Epoch) : NetworkEvent

    data class JoinAttemptRejected(val remoteAddress: String, val reason: RejectReason) : NetworkEvent

    data class Disconnected(val reason: LeaveReason) : NetworkEvent

    data class OwnershipChanged(val node: Node, val previousOwner: PlayerId, val newOwner: PlayerId) : NetworkEvent

    data class LevelLoaded(val level: Node, val sequence: Int) : NetworkEvent

    data class LevelStarted(val sequence: Int) : NetworkEvent
}
