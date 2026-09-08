package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.codec.CodecException
import ch.hippmann.godot.replication.core.codec.format.ReplicationBinary
import kotlinx.serialization.KSerializer

/**
 * Control messages are serialized with [ReplicationBinary]; state and snapshot payloads carry schema dependent
 * entries and are framed by the replication engine itself, so they are not registered here.
 */
public object MessageCodec {
    private val format = ReplicationBinary.Default
    private val serializers: Map<MessageType, KSerializer<out WireMessage>> = mapOf(
        MessageType.JOIN_REQUEST to JoinRequest.serializer(),
        MessageType.REDIRECT to Redirect.serializer(),
        MessageType.CHALLENGE to Challenge.serializer(),
        MessageType.JOIN_PROOF to JoinProof.serializer(),
        MessageType.ADMITTED to Admitted.serializer(),
        MessageType.REJECTED to Rejected.serializer(),
        MessageType.HELLO to Hello.serializer(),
        MessageType.HELLO_ACCEPTED to HelloAccepted.serializer(),
        MessageType.MEMBER_JOINED to MemberJoined.serializer(),
        MessageType.LEAVE to Leave.serializer(),
        MessageType.MEMBERSHIP_UPDATE to MembershipUpdate.serializer(),
        MessageType.KICK to Kick.serializer(),
        MessageType.LOBBY_UPDATE to LobbyUpdate.serializer(),
        MessageType.PLAYER_UPDATE to PlayerUpdate.serializer(),
        MessageType.LOBBY_COMMAND to LobbyCommand.serializer(),
        MessageType.LEVEL_LOAD to LevelLoad.serializer(),
        MessageType.LEVEL_LOADED to LevelLoaded.serializer(),
        MessageType.LEVEL_START to LevelStart.serializer(),
        MessageType.DESPAWN to Despawn.serializer(),
        MessageType.OWNERSHIP_CHANGED to OwnershipChanged.serializer(),
        MessageType.OWNERSHIP_REQUEST to OwnershipRequest.serializer(),
        MessageType.OWNERSHIP_REPLY to OwnershipReply.serializer(),
        MessageType.TIME_SYNC to TimeSync.serializer(),
        MessageType.CUSTOM to Custom.serializer(),
        MessageType.SNAPSHOT_REQUEST to SnapshotRequest.serializer(),
        MessageType.SNAPSHOT_DONE to SnapshotDone.serializer(),
    )

    public fun encode(message: WireMessage): ByteArray {
        val writer = ByteWriter()
        writer.writeByte(message.type.id)
        @Suppress("UNCHECKED_CAST")
        val serializer = serializerFor(message.type) as KSerializer<WireMessage>
        format.encodeTo(writer, serializer, message)
        return writer.toByteArray()
    }

    public fun decode(bytes: ByteArray): WireMessage {
        val reader = ByteReader(bytes)
        val type = MessageType.fromId(reader.readByte())
            ?: throw CodecException("Unknown message type ${bytes.firstOrNull()}")
        return format.decodeFrom(reader, serializerFor(type))
    }

    public fun isControlMessage(type: MessageType): Boolean = type in serializers

    private fun serializerFor(type: MessageType): KSerializer<out WireMessage> =
        serializers[type] ?: throw CodecException("Message type $type is not a control message")
}
