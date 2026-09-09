package ch.hippmann.godot.replication.core.rendezvous

/** The datagram a socket sends to the service so the service can tell it its public address: a marker and a token. */
public object ProbeCodec {
    private val MARKER = byteArrayOf('R'.code.toByte(), 'V'.code.toByte(), 'Z'.code.toByte(), 'P'.code.toByte())
    public const val SIZE: Int = 12

    public fun encode(token: Long): ByteArray {
        val bytes = ByteArray(SIZE)
        MARKER.copyInto(bytes)
        for (index in 0 until 8) bytes[4 + index] = (token ushr (56 - index * 8)).toByte()
        return bytes
    }

    public fun decode(bytes: ByteArray): Long? {
        if (bytes.size != SIZE) return null
        for (index in MARKER.indices) if (bytes[index] != MARKER[index]) return null
        var token = 0L
        for (index in 0 until 8) token = (token shl 8) or (bytes[4 + index].toLong() and 0xFF)
        return token
    }
}
