package ch.hippmann.godot.replication.serializer.math

import godot.core.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class ColorSurrogate(
    val r: Double,
    val g: Double,
    val b: Double,
    val a: Double,
)

class ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = ColorSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Color {
        val surrogate = decoder.decodeSerializableValue(ColorSurrogate.serializer())
        return Color(
            r = surrogate.r,
            g = surrogate.g,
            b = surrogate.b,
            a = surrogate.a,
        )
    }

    override fun serialize(encoder: Encoder, value: Color) {
        val surrogate = ColorSurrogate(
            r = value.r,
            g = value.g,
            b = value.b,
            a = value.a,
        )
        encoder.encodeSerializableValue(ColorSurrogate.serializer(), surrogate)
    }
}