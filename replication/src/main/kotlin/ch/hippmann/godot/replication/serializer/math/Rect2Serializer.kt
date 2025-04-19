package ch.hippmann.godot.replication.serializer.math

import godot.core.Rect2
import godot.core.Vector2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class Rect2Surrogate(
    @Serializable(Vector2Serializer::class) val position: Vector2,
    @Serializable(Vector2Serializer::class) val size: Vector2,
)

class Rect2Serializer : KSerializer<Rect2> {
    override val descriptor: SerialDescriptor = Rect2Surrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Rect2 {
        val surrogate = decoder.decodeSerializableValue(Rect2Surrogate.serializer())
        return Rect2(
            p_position = surrogate.position,
            p_size = surrogate.size,
        )
    }

    override fun serialize(encoder: Encoder, value: Rect2) {
        val surrogate = Rect2Surrogate(
            position = value.position,
            size = value.size,
        )
        encoder.encodeSerializableValue(Rect2Surrogate.serializer(), surrogate)
    }
}