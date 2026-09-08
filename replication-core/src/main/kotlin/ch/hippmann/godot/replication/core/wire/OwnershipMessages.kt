package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.session.Epoch
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class OwnershipEntry(val networkId: NetworkId, val newOwner: PlayerId)

@Serializable
public data class OwnershipChanged(val epoch: Epoch, val entries: List<OwnershipEntry>) : WireMessage {
    override val type: MessageType get() = MessageType.OWNERSHIP_CHANGED
}

@Serializable
public data class OwnershipRequest(val networkId: NetworkId) : WireMessage {
    override val type: MessageType get() = MessageType.OWNERSHIP_REQUEST
}

@Serializable
public data class OwnershipReply(val networkId: NetworkId, val granted: Boolean) : WireMessage {
    override val type: MessageType get() = MessageType.OWNERSHIP_REPLY
}
