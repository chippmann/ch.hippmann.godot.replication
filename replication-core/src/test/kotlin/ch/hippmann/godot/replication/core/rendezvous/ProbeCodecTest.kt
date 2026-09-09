package ch.hippmann.godot.replication.core.rendezvous

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProbeCodecTest {
    @Test
    fun `a probe round trips its token`() {
        for (token in listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 0x1122334455667788L)) {
            assertEquals(token, ProbeCodec.decode(ProbeCodec.encode(token)))
        }
    }

    @Test
    fun `other datagrams are not probes`() {
        assertNull(ProbeCodec.decode(ByteArray(0)))
        assertNull(ProbeCodec.decode(ByteArray(12)))
        assertNull(ProbeCodec.decode(ProbeCodec.encode(7) + byteArrayOf(0)))
    }
}
