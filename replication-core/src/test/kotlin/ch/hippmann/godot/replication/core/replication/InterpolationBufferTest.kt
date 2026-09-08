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
    fun `extrapolation is capped`() {
        val buffer = InterpolationBuffer<Double>()
        buffer.push(1000, 0.0)
        buffer.push(1100, 10.0)
        assertEquals(15.0, buffer.sample(1150, linear, 100)!!)
        assertEquals(20.0, buffer.sample(1500, linear, 100)!!)
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
