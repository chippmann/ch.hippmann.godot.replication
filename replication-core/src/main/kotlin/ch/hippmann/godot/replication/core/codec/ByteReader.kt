package ch.hippmann.godot.replication.core.codec

public class ByteReader(
    private val bytes: ByteArray,
    offset: Int = 0,
    private val end: Int = bytes.size,
) {
    public var position: Int = offset
        private set

    public val remaining: Int
        get() = end - position

    public fun readByte(): Int {
        require(1)
        return bytes[position++].toInt() and 0xFF
    }

    public fun readBoolean(): Boolean = readByte() != 0

    public fun readInt16(): Int {
        require(2)
        val value = (bytes[position].toInt() and 0xFF) or (bytes[position + 1].toInt() shl 8)
        position += 2
        return value
    }

    public fun readInt32(): Int {
        require(4)
        var value = 0
        repeat(4) { index -> value = value or ((bytes[position + index].toInt() and 0xFF) shl (8 * index)) }
        position += 4
        return value
    }

    public fun readInt64(): Long {
        require(8)
        var value = 0L
        repeat(8) { index -> value = value or ((bytes[position + index].toLong() and 0xFF) shl (8 * index)) }
        position += 8
        return value
    }

    public fun readVarLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = readByte()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw CodecException("Varint longer than 64 bits at position $position")
        }
    }

    public fun readVarInt(): Int {
        val value = readVarLong()
        if (value > 0xFFFF_FFFFL) throw CodecException("Varint does not fit into 32 bits at position $position")
        return value.toInt()
    }

    public fun readZigZagInt(): Int {
        val raw = readVarInt()
        return (raw ushr 1) xor -(raw and 1)
    }

    public fun readZigZagLong(): Long {
        val raw = readVarLong()
        return (raw ushr 1) xor -(raw and 1L)
    }

    public fun readFloat32(): Float = Float.fromBits(readInt32())

    public fun readFloat64(): Double = Double.fromBits(readInt64())

    public fun readHalf(): Float = HalfFloat.fromHalfBits(readInt16())

    public fun readString(): String = readBytes().decodeToString()

    public fun readBytes(): ByteArray = readRawBytes(readVarInt())

    public fun readRawBytes(length: Int): ByteArray {
        require(length)
        val value = bytes.copyOfRange(position, position + length)
        position += length
        return value
    }

    public fun skip(length: Int) {
        require(length)
        position += length
    }

    private fun require(length: Int) {
        if (length < 0 || position + length > end) {
            throw CodecException("Cannot read $length byte(s) at position $position, only $remaining remaining")
        }
    }
}
