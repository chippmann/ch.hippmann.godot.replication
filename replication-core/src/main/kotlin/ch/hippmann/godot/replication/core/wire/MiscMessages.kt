package ch.hippmann.godot.replication.core.wire

import kotlinx.serialization.Serializable

@Serializable
public data class TimeSync(val sendTimeMilliseconds: Long, val tick: Int) : WireMessage {
    override val type: MessageType get() = MessageType.TIME_SYNC
}

@Serializable
public class Custom(public val typeHash: Int, public val bytes: ByteArray) : WireMessage {
    override val type: MessageType get() = MessageType.CUSTOM
}

@Serializable
public data class SnapshotRequest(val levelSequence: Int) : WireMessage {
    override val type: MessageType get() = MessageType.SNAPSHOT_REQUEST
}

@Serializable
public data class SnapshotDone(val levelSequence: Int, val nodeCount: Int) : WireMessage {
    override val type: MessageType get() = MessageType.SNAPSHOT_DONE
}
