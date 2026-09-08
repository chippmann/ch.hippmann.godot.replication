package ch.hippmann.godot.replication.core.replication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InterpolationBufferTest {
    private val linear = Interpolator<Double> { from, to, weight -> from + (to - from) * weight }

    @Test
    fun `samples interpolate between neighbours`() {
        val buffer = InterpolationBuffer<Double>()
        buffer.push(1000, 0.0)
        buffer.push(1100, 10.0)
        buffer.push(1200, 30.0)
        assertEquals(5.0, buffer.sample(1050, linear, 100)!!)
        assertEquals(20.0, buffer.sample(1150, linear, 100)!!)
        assertEquals(0.0, buffer.sample(900, linear, 100)!!)
    }

    @Test
    fun `extrapolation is capped and eases back onto the last sample`() {
        val buffer = InterpolationBuffer<Double>()
        buffer.push(1000, 0.0)
        buffer.push(1100, 10.0)
        assertEquals(15.0, buffer.sample(1150, linear, 100)!!)
        assertEquals(20.0, buffer.sample(1200, linear, 100)!!)
        assertEquals(15.0, buffer.sample(1250, linear, 100)!!)
        assertEquals(10.0, buffer.sample(1500, linear, 100)!!)
        assertEquals(10.0, buffer.sample(1500, linear, 0)!!)
    }

    @Test
    fun `the recommended delay follows the largest recent gap`() {
        val buffer = InterpolationBuffer<Double>()
        buffer.push(1000, 0.0)
        buffer.push(1033, 1.0)
        assertEquals(49, buffer.recommendedDelayMilliseconds)
        buffer.push(1133, 2.0)
        assertEquals(150, buffer.recommendedDelayMilliseconds)
        repeat(60) { index -> buffer.push(1166 + index * 33L, index.toDouble()) }
        assertEquals(49, buffer.recommendedDelayMilliseconds)
        buffer.push(9000, 0.0)
        assertEquals(InterpolationBuffer.MAXIMUM_RECOMMENDED_DELAY_MILLISECONDS, buffer.recommendedDelayMilliseconds)
    }

    @Test
    fun `out of order and overflowing samples are handled`() {
        val buffer = InterpolationBuffer<Double>(capacity = 3)
        assertNull(buffer.sample(0, linear, 0))
        buffer.push(1000, 1.0)
        buffer.push(900, 99.0)
        buffer.push(1100, 2.0)
        buffer.push(1200, 3.0)
        buffer.push(1300, 4.0)
        assertEquals(4.0, buffer.latest)
        assertEquals(2.5, buffer.sample(1150, linear, 0)!!)
    }
}
