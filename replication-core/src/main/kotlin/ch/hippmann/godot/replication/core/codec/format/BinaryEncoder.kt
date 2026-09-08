package ch.hippmann.godot.replication.core.codec.format

import ch.hippmann.godot.replication.core.codec.ByteWriter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal class BinaryEncoder(
    private val writer: ByteWriter,
    override val serializersModule: SerializersModule,
) : AbstractEncoder() {

    override fun encodeBoolean(value: Boolean): Unit = writer.writeBoolean(value)

    override fun encodeByte(value: Byte): Unit = writer.writeByte(value.toInt())

    override fun encodeShort(value: Short): Unit = writer.writeZigZagInt(value.toInt())

    override fun encodeInt(value: Int): Unit = writer.writeZigZagInt(value)

    override fun encodeLong(value: Long): Unit = writer.writeZigZagLong(value)

    override fun encodeFloat(value: Float): Unit = writer.writeFloat32(value)

    override fun encodeDouble(value: Double): Unit = writer.writeFloat64(value)

    override fun encodeChar(value: Char): Unit = writer.writeVarInt(value.code)

    override fun encodeString(value: String): Unit = writer.writeString(value)

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int): Unit = writer.writeVarInt(index)

    override fun encodeNull(): Unit = writer.writeByte(0)

    override fun encodeNotNullMark(): Unit = writer.writeByte(1)

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        writer.writeVarInt(collectionSize)
        return this
    }
}
