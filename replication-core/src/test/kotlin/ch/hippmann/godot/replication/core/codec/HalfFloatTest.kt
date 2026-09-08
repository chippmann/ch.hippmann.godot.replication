package ch.hippmann.godot.replication.core.codec

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HalfFloatTest {

    @Test
    fun `exactly representable values survive the round trip`() {
        for (value in listOf(0f, -0f, 1f, -1f, 0.5f, 1.5f, 1024f, -2048f, 65504f, 0.0009765625f)) {
            assertEquals(value, HalfFloat.fromHalfBits(HalfFloat.toHalfBits(value)), "value $value")
        }
    }

    @Test
    fun `other values keep about three significant digits`() {
        for (value in listOf(3.14159f, -0.333f, 123.456f, 0.01f, 999.9f)) {
            val restored = HalfFloat.fromHalfBits(HalfFloat.toHalfBits(value))
            assertTrue(abs(restored - value) <= abs(value) * 0.001f, "value $value became $restored")
        }
    }

    @Test
    fun `values beyond the half range become infinity and specials survive`() {
        assertEquals(Float.POSITIVE_INFINITY, HalfFloat.fromHalfBits(HalfFloat.toHalfBits(100_000f)))
        assertEquals(Float.NEGATIVE_INFINITY, HalfFloat.fromHalfBits(HalfFloat.toHalfBits(-100_000f)))
        assertTrue(HalfFloat.fromHalfBits(HalfFloat.toHalfBits(Float.NaN)).isNaN())
        assertEquals(0x3C00, HalfFloat.toHalfBits(1f))
    }
}
