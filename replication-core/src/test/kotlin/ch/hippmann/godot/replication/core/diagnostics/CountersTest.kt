package ch.hippmann.godot.replication.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountersTest {

    @Test
    fun `windows close after their duration and expose whole windows only`() {
        val counters = Counters(1_000)
        counters.add("packets.out", 3)
        assertFalse(counters.advance(100))
        counters.add("packets.out", 2)
        assertEquals(0, counters["packets.out"])
        assertTrue(counters.advance(1_100))
        assertEquals(5, counters["packets.out"])
        counters.add("packets.out")
        assertEquals(5, counters["packets.out"])
        assertTrue(counters.advance(2_200))
        assertEquals(1, counters["packets.out"])
    }
}
