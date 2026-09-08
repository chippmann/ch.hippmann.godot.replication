package ch.hippmann.godot.replication.core.replication

import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeltaPacketsTest {

    @Test
    fun `entries round trip with their mask and values`() {
        val writer = DeltaWriter(MessageType.STATE_DELTA, tick = 42, sendTimeMilliseconds = 123_456)
        writer.entry(NetworkId.spawned(PlayerId(3), 1), mask = 0b101) { values -> IntCodec.write(values, 7); StringCodec.write(values, "wrench") }
        writer.entry(NetworkId.scenePlaced(99), mask = 0b10) { values -> BooleanCodec.write(values, true) }
        val packets = writer.finish()
        assertEquals(1, packets.size)

        val reader = DeltaReader.parse(packets.single())
        assertEquals(MessageType.STATE_DELTA, reader.type)
        assertEquals(42, reader.tick)
        assertEquals(123_456L, reader.sendTimeMilliseconds)
        val entries = mutableListOf<Triple<NetworkId, Long, Any>>()
        reader.forEachEntry { entry ->
            val value: Any = if (entry.mask == 0b101L) IntCodec.read(entry.values) to StringCodec.read(entry.values) else BooleanCodec.read(entry.values)
            entries += Triple(entry.networkId, entry.mask, value)
        }
        assertEquals(NetworkId.spawned(PlayerId(3), 1), entries[0].first)
        assertEquals(7 to "wrench", entries[0].third)
        assertEquals(true, entries[1].third)
    }

    @Test
    fun `packets split at the budget and unknown entries can be skipped`() {
        val writer = DeltaWriter(MessageType.STATE_DELTA, 1, 0)
        repeat(40) { index -> writer.entry(NetworkId.spawned(PlayerId(2), index), 1) { values -> ByteArrayCodec.write(values, ByteArray(100)) } }
        val packets = writer.finish()
        assertTrue(packets.size >= 4, "expected several packets, got ${packets.size}")
        assertTrue(packets.all { packet -> packet.size <= PacketBudget.MAXIMUM_PAYLOAD_BYTES + 128 })

        var seen = 0
        for (packet in packets) DeltaReader.parse(packet).forEachEntry { entry -> if (entry.networkId.counter % 2 == 0) seen++ }
        assertEquals(20, seen)
    }

    @Test
    fun `an empty writer produces no packets`() {
        assertEquals(emptyList(), DeltaWriter(MessageType.STATE_RELIABLE, 0, 0).finish())
    }
}
