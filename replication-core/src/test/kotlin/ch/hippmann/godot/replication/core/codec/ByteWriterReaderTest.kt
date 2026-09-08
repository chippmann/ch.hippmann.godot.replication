package ch.hippmann.godot.replication.core.codec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteWriterReaderTest {

    @Test
    fun `fixed width values round trip in little endian order`() {
        val writer = ByteWriter(4)
        writer.writeByte(0xAB)
        writer.writeInt16(0x1234)
        writer.writeInt32(-123456)
        writer.writeInt64(Long.MIN_VALUE + 7)
        writer.writeFloat32(3.25f)
        writer.writeFloat64(-6.125)
        writer.writeBoolean(true)

        val reader = ByteReader(writer.toByteArray())
        assertEquals(0xAB, reader.readByte())
        assertEquals(0x1234, reader.readInt16())
        assertEquals(-123456, reader.readInt32())
        assertEquals(Long.MIN_VALUE + 7, reader.readInt64())
        assertEquals(3.25f, reader.readFloat32())
        assertEquals(-6.125, reader.readFloat64())
        assertEquals(true, reader.readBoolean())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `varints use one byte below 128 and grow with the magnitude`() {
        val samples = listOf(0L, 1L, 127L, 128L, 300L, 65_535L, Int.MAX_VALUE.toLong(), 1L shl 40, Long.MAX_VALUE, -1L)
        for (sample in samples) {
            val writer = ByteWriter()
            writer.writeVarLong(sample)
            val bytes = writer.toByteArray()
            assertEquals(sample, ByteReader(bytes).readVarLong(), "value $sample")
            if (sample in 0..127) assertEquals(1, bytes.size)
        }
    }

    @Test
    fun `zigzag keeps small negative numbers short`() {
        val writer = ByteWriter()
        writer.writeZigZagInt(-1)
        writer.writeZigZagInt(Int.MIN_VALUE)
        writer.writeZigZagLong(-64)
        writer.writeZigZagLong(Long.MAX_VALUE)
        val bytes = writer.toByteArray()

        val reader = ByteReader(bytes)
        assertEquals(-1, reader.readZigZagInt())
        assertEquals(Int.MIN_VALUE, reader.readZigZagInt())
        assertEquals(-64L, reader.readZigZagLong())
        assertEquals(Long.MAX_VALUE, reader.readZigZagLong())
        assertEquals(1 + 5 + 1 + 10, bytes.size)
    }

    @Test
    fun `strings and byte arrays carry their length`() {
        val writer = ByteWriter()
        writer.writeString("Mara's arena")
        writer.writeBytes(byteArrayOf(1, 2, 3))
        writer.writeString("")

        val reader = ByteReader(writer.toByteArray())
        assertEquals("Mara's arena", reader.readString())
        assertEquals(listOf<Byte>(1, 2, 3), reader.readBytes().toList())
        assertEquals("", reader.readString())
    }

    @Test
    fun `reading past the end fails instead of returning garbage`() {
        val reader = ByteReader(byteArrayOf(1, 2))
        reader.readInt16()
        assertFailsWith<CodecException> { reader.readByte() }
        assertFailsWith<CodecException> { ByteReader(byteArrayOf(0x80.toByte())).readVarLong() }
    }
}
