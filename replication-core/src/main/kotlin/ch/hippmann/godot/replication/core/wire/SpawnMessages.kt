package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import kotlinx.serialization.Serializable

public sealed interface ParentReference {
    public data class Networked(val networkId: NetworkId) : ParentReference

    public data class Path(val absolutePath: String) : ParentReference
}

public enum class OwnerLeavePolicyId { DESPAWN, TRANSFER_TO_MASTER, TRANSFER_TO }

public enum class OwnershipPolicyId { FIXED, TRANSFERABLE, REQUEST_REQUIRED }

/** Hand coded because its tail carries a schema dependent state entry that only the replication engine can decode. */
public class Spawn(
    public val networkId: NetworkId,
    public val scenePath: String,
    public val parent: ParentReference,
    public val name: String,
    public val owner: PlayerId,
    public val ownerLeavePolicy: OwnerLeavePolicyId,
    public val ownershipPolicy: OwnershipPolicyId,
    public val schemaHash: Int,
    public val spawnData: ByteArray,
    public val initialState: ByteArray,
) {
    public fun encode(): ByteArray {
        val writer = ByteWriter(256)
        writer.writeByte(MessageType.SPAWN.id)
        writer.writeVarLong(networkId.value)
        writer.writeString(scenePath)
        when (parent) {
            is ParentReference.Networked -> {
                writer.writeByte(PARENT_NETWORKED)
                writer.writeVarLong(parent.networkId.value)
            }
            is ParentReference.Path -> {
                writer.writeByte(PARENT_PATH)
                writer.writeString(parent.absolutePath)
            }
        }
        writer.writeString(name)
        writer.writeVarInt(owner.value)
        writer.writeByte(ownerLeavePolicy.ordinal)
        writer.writeByte(ownershipPolicy.ordinal)
        writer.writeInt32(schemaHash)
        writer.writeBytes(spawnData)
        writer.writeBytes(initialState)
        return writer.toByteArray()
    }

    public companion object {
        private const val PARENT_NETWORKED = 0
        private const val PARENT_PATH = 1

        public fun decode(bytes: ByteArray): Spawn {
            val reader = ByteReader(bytes)
            require(reader.readByte() == MessageType.SPAWN.id) { "Not a spawn message" }
            val networkId = NetworkId(reader.readVarLong())
            val scenePath = reader.readString()
            val parent = when (reader.readByte()) {
                PARENT_NETWORKED -> ParentReference.Networked(NetworkId(reader.readVarLong()))
                else -> ParentReference.Path(reader.readString())
            }
            return Spawn(
                networkId = networkId,
                scenePath = scenePath,
                parent = parent,
                name = reader.readString(),
                owner = PlayerId(reader.readVarInt()),
                ownerLeavePolicy = OwnerLeavePolicyId.entries[reader.readByte()],
                ownershipPolicy = OwnershipPolicyId.entries[reader.readByte()],
                schemaHash = reader.readInt32(),
                spawnData = reader.readBytes(),
                initialState = reader.readBytes(),
            )
        }
    }
}

@Serializable
public data class Despawn(val networkId: NetworkId) : WireMessage {
    override val type: MessageType get() = MessageType.DESPAWN
}
