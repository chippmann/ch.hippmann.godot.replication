package ch.hippmann.godot.replication.core.codec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuantizationTest {

    private fun roundTrip(quantization: Quantization, value: Double, precision: Precision = Precision.Single): Pair<Double, Int> {
        val writer = ByteWriter()
        quantization.write(writer, value, precision)
        val bytes = writer.toByteArray()
        return quantization.read(ByteReader(bytes), precision) to bytes.size
    }

    @Test
    fun `no quantization honors the requested precision`() {
        assertEquals(1.5 to 4, roundTrip(Quantization.None, 1.5, Precision.Single))
        assertEquals(1.1 to 8, roundTrip(Quantization.None, 1.1, Precision.Double))
    }

    @Test
    fun `fixed quantization snaps to the step and stays compact`() {
        val (restored, size) = roundTrip(Quantization.Fixed(0.01), 12.3456)
        assertEquals(12.35, restored, 1e-9)
        assertEquals(2, size)
        assertEquals(-0.5 to 1, roundTrip(Quantization.Fixed(0.5), -0.6))
    }

    @Test
    fun `half quantization uses two bytes`() {
        val (restored, size) = roundTrip(Quantization.Half, 2.5)
        assertEquals(2.5, restored)
        assertEquals(2, size)
    }

    @Test
    fun `a non positive step is rejected`() {
        assertFailsWith<IllegalArgumentException> { Quantization.Fixed(0.0) }
    }
}
