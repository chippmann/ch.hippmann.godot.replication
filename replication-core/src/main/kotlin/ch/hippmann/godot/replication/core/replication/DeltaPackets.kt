package ch.hippmann.godot.replication.core.replication

import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.wire.MessageType

public object PacketBudget {
    /** Below the usual path MTU so unreliable packets are never fragmented by ENet. */
    public const val MAXIMUM_PAYLOAD_BYTES: Int = 1200
}

/**
 * Packet layout: type u8, tick varint, send time varlong, then entries of network id, property bitset, byte length
 * and the values in property index order. The length lets a receiver skip entries of nodes it does not know.
 */
public class DeltaWriter(private val type: MessageType, private val tick: Int, private val sendTimeMilliseconds: Long) {
    private val packets = mutableListOf<ByteArray>()
    private val entry = ByteWriter(512)
    private var current = newPacket()
    private var entriesInCurrent = 0

    public fun entry(networkId: NetworkId, mask: Long, values: (ByteWriter) -> Unit) {
        entry.reset()
        values(entry)
        val header = ByteWriter(24)
        header.writeVarLong(networkId.value)
        header.writeVarLong(mask)
        header.writeVarInt(entry.size)
        val entrySize = header.size + entry.size
        if (entriesInCurrent > 0 && current.size + entrySize > PacketBudget.MAXIMUM_PAYLOAD_BYTES) {
            packets += current.toByteArray()
            current = newPacket()
            entriesInCurrent = 0
        }
        current.writeRawBytes(header.toByteArray())
        current.writeRawBytes(entry.toByteArray(), 0, entry.size)
        entriesInCurrent++
    }

    public fun finish(): List<ByteArray> {
        if (entriesInCurrent > 0) packets += current.toByteArray()
        return packets
    }

    private fun newPacket(): ByteWriter = ByteWriter(PacketBudget.MAXIMUM_PAYLOAD_BYTES + 64).also { writer ->
        writer.writeByte(type.id)
        writer.writeVarInt(tick)
        writer.writeVarLong(sendTimeMilliseconds)
    }
}

public class DeltaEntry(public val networkId: NetworkId, public val mask: Long, public val values: ByteReader)

public class DeltaReader private constructor(
    public val type: MessageType,
    public val tick: Int,
    public val sendTimeMilliseconds: Long,
    private val reader: ByteReader,
) {
    public fun forEachEntry(action: (DeltaEntry) -> Unit) {
        while (reader.remaining > 0) {
            val networkId = NetworkId(reader.readVarLong())
            val mask = reader.readVarLong()
            val length = reader.readVarInt()
            val values = reader.readRawBytes(length)
            action(DeltaEntry(networkId, mask, ByteReader(values)))
        }
    }

    public companion object {
        public fun parse(bytes: ByteArray): DeltaReader {
            val reader = ByteReader(bytes)
            val type = MessageType.fromId(reader.readByte()) ?: throw IllegalArgumentException("Not a state packet")
            return DeltaReader(type, reader.readVarInt(), reader.readVarLong(), reader)
        }
    }
}
