package ch.hippmann.godot.replication.core.wire

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscoveryCodecTest {

    @Test
    fun `probes and answers round trip`() {
        assertEquals(DiscoveryProbe(), DiscoveryCodec.decodeProbe(DiscoveryCodec.encodeProbe(DiscoveryProbe())))
        val answer = DiscoveryAnswer(PROTOCOL_VERSION, 7777, "Mara's arena", 2, 8, passwordRequired = true)
        assertEquals(answer, DiscoveryCodec.decodeAnswer(DiscoveryCodec.encodeAnswer(answer)))
    }

    @Test
    fun `foreign datagrams are ignored`() {
        assertNull(DiscoveryCodec.decodeProbe(byteArrayOf(1, 2, 3)))
        assertNull(DiscoveryCodec.decodeProbe(DiscoveryCodec.encodeAnswer(DiscoveryAnswer(1, 1, "", 0, 0, false))))
        assertNull(DiscoveryCodec.decodeAnswer("RPLD".encodeToByteArray() + byteArrayOf(2)))
    }
}
