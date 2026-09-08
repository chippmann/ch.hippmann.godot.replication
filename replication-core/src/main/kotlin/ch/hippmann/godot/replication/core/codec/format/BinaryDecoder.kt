package ch.hippmann.godot.replication.core.codec.format

import ch.hippmann.godot.replication.core.codec.ByteReader
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal class BinaryDecoder(
    private val reader: ByteReader,
    override val serializersModule: SerializersModule,
) : AbstractDecoder() {
    private var elementIndex = 0

    override fun decodeBoolean(): Boolean = reader.readBoolean()

    override fun decodeByte(): Byte = reader.readByte().toByte()

    override fun decodeShort(): Short = reader.readZigZagInt().toShort()

    override fun decodeInt(): Int = reader.readZigZagInt()

    override fun decodeLong(): Long = reader.readZigZagLong()

    override fun decodeFloat(): Float = reader.readFloat32()

    override fun decodeDouble(): Double = reader.readFloat64()

    override fun decodeChar(): Char = reader.readVarInt().toChar()

    override fun decodeString(): String = reader.readString()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = reader.readVarInt()

    override fun decodeNotNullMark(): Boolean = reader.readBoolean()

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = reader.readVarInt()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder = BinaryDecoder(reader, serializersModule)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (elementIndex < descriptor.elementsCount) elementIndex++ else CompositeDecoder.DECODE_DONE
}
