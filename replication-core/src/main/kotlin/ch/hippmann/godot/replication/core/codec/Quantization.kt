package ch.hippmann.godot.replication.core.codec

import kotlin.math.roundToLong

public sealed interface Quantization {
    public val id: Int

    public fun write(writer: ByteWriter, value: Double, precision: Precision)

    public fun read(reader: ByteReader, precision: Precision): Double

    public object None : Quantization {
        override val id: Int = 0

        override fun write(writer: ByteWriter, value: Double, precision: Precision) {
            when (precision) {
                Precision.Single -> writer.writeFloat32(value.toFloat())
                Precision.Double -> writer.writeFloat64(value)
            }
        }

        override fun read(reader: ByteReader, precision: Precision): Double = when (precision) {
            Precision.Single -> reader.readFloat32().toDouble()
            Precision.Double -> reader.readFloat64()
        }
    }

    public object Half : Quantization {
        override val id: Int = 1

        override fun write(writer: ByteWriter, value: Double, precision: Precision): Unit = writer.writeHalf(value.toFloat())

        override fun read(reader: ByteReader, precision: Precision): Double = reader.readHalf().toDouble()
    }

    public data class Fixed(val step: Double) : Quantization {
        init {
            require(step > 0.0) { "The quantization step must be positive, was $step" }
        }

        override val id: Int = 2

        override fun write(writer: ByteWriter, value: Double, precision: Precision): Unit =
            writer.writeZigZagLong((value / step).roundToLong())

        override fun read(reader: ByteReader, precision: Precision): Double = reader.readZigZagLong() * step
    }
}
