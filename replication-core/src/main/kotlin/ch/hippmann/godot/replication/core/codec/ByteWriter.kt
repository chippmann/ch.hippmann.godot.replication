package ch.hippmann.godot.replication.core.codec

public class ByteWriter(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity)

    public var size: Int = 0
        private set

    public fun reset() {
        size = 0
    }

    public fun toByteArray(): ByteArray = buffer.copyOf(size)

    public fun writeByte(value: Int) {
        ensureCapacity(1)
        buffer[size++] = value.toByte()
    }

    public fun writeBoolean(value: Boolean): Unit = writeByte(if (value) 1 else 0)

    public fun writeInt16(value: Int) {
        ensureCapacity(2)
        buffer[size++] = value.toByte()
        buffer[size++] = (value shr 8).toByte()
    }

    public fun writeInt32(value: Int) {
        ensureCapacity(4)
        repeat(4) { index -> buffer[size++] = (value shr (8 * index)).toByte() }
    }

    public fun writeInt64(value: Long) {
        ensureCapacity(8)
        repeat(8) { index -> buffer[size++] = (value shr (8 * index)).toByte() }
    }

    public fun writeVarLong(value: Long) {
        var remaining = value
        while (remaining and 0x7FL.inv() != 0L) {
            writeByte(((remaining and 0x7F) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        writeByte(remaining.toInt())
    }

    public fun writeVarInt(value: Int): Unit = writeVarLong(value.toLong() and 0xFFFF_FFFFL)

    public fun writeZigZagInt(value: Int): Unit = writeVarLong(((value shl 1) xor (value shr 31)).toLong() and 0xFFFF_FFFFL)

    public fun writeZigZagLong(value: Long): Unit = writeVarLong((value shl 1) xor (value shr 63))

    public fun writeFloat32(value: Float): Unit = writeInt32(value.toRawBits())

    public fun writeFloat64(value: Double): Unit = writeInt64(value.toRawBits())

    public fun writeHalf(value: Float): Unit = writeInt16(HalfFloat.toHalfBits(value))

    public fun writeString(value: String) {
        val encoded = value.encodeToByteArray()
        writeVarInt(encoded.size)
        writeRawBytes(encoded)
    }

    public fun writeBytes(value: ByteArray) {
        writeVarInt(value.size)
        writeRawBytes(value)
    }

    public fun writeRawBytes(value: ByteArray, offset: Int = 0, length: Int = value.size - offset) {
        ensureCapacity(length)
        value.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    private fun ensureCapacity(extra: Int) {
        if (size + extra > buffer.size) {
            buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
        }
    }
}
