package ch.hippmann.godot.replication.core.codec.format

import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Positional binary format: no field tags, varint sizes, one marker byte per nullable value. Both sides must share
 * the same class schema, which the wire protocol guarantees with its version and schema hashes.
 */
public class ReplicationBinary(
    override val serializersModule: SerializersModule = EmptySerializersModule(),
) : BinaryFormat {

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val writer = ByteWriter()
        encodeTo(writer, serializer, value)
        return writer.toByteArray()
    }

    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
        decodeFrom(ByteReader(bytes), deserializer)

    public fun <T> encodeTo(writer: ByteWriter, serializer: SerializationStrategy<T>, value: T) {
        BinaryEncoder(writer, serializersModule).encodeSerializableValue(serializer, value)
    }

    public fun <T> decodeFrom(reader: ByteReader, deserializer: DeserializationStrategy<T>): T =
        BinaryDecoder(reader, serializersModule).decodeSerializableValue(deserializer)

    public companion object {
        public val Default: ReplicationBinary = ReplicationBinary()
    }
}
