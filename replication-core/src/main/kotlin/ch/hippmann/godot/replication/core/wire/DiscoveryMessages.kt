package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.codec.CodecException
import ch.hippmann.godot.replication.core.codec.format.ReplicationBinary
import kotlinx.serialization.Serializable

/** LAN discovery runs over plain UDP broadcast, outside the ENet mesh, so it has its own framing. */
@Serializable
public data class DiscoveryProbe(val protocolVersion: Int = PROTOCOL_VERSION)

@Serializable
public data class DiscoveryAnswer(
    val protocolVersion: Int,
    val sessionPort: Int,
    val lobbyName: String,
    val playerCount: Int,
    val maximumPlayers: Int,
    val passwordRequired: Boolean,
)

public object DiscoveryCodec {
    private const val PROBE_MARKER = 0x01
    private const val ANSWER_MARKER = 0x02
    private val magic = "RPLD".encodeToByteArray()

    public fun encodeProbe(probe: DiscoveryProbe): ByteArray = encode(PROBE_MARKER) { writer ->
        ReplicationBinary.Default.encodeTo(writer, DiscoveryProbe.serializer(), probe)
    }

    public fun encodeAnswer(answer: DiscoveryAnswer): ByteArray = encode(ANSWER_MARKER) { writer ->
        ReplicationBinary.Default.encodeTo(writer, DiscoveryAnswer.serializer(), answer)
    }

    public fun decodeProbe(bytes: ByteArray): DiscoveryProbe? = decode(bytes, PROBE_MARKER) { reader ->
        ReplicationBinary.Default.decodeFrom(reader, DiscoveryProbe.serializer())
    }

    public fun decodeAnswer(bytes: ByteArray): DiscoveryAnswer? = decode(bytes, ANSWER_MARKER) { reader ->
        ReplicationBinary.Default.decodeFrom(reader, DiscoveryAnswer.serializer())
    }

    private fun encode(marker: Int, payload: (ByteWriter) -> Unit): ByteArray {
        val writer = ByteWriter()
        writer.writeRawBytes(magic)
        writer.writeByte(marker)
        payload(writer)
        return writer.toByteArray()
    }

    private fun <T> decode(bytes: ByteArray, marker: Int, payload: (ByteReader) -> T): T? {
        if (bytes.size < magic.size + 1 || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) return null
        val reader = ByteReader(bytes, magic.size)
        if (reader.readByte() != marker) return null
        return try {
            payload(reader)
        } catch (exception: CodecException) {
            null
        }
    }
}
