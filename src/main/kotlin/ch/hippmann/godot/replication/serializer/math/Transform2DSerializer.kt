package ch.hippmann.godot.replication.serializer.math

import godot.core.Transform2D
import godot.core.Vector2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class Transform2DSurrogate(
    @Serializable(with = Vector2Serializer::class) val x: Vector2,
    @Serializable(with = Vector2Serializer::class) val y: Vector2,
    @Serializable(with = Vector2Serializer::class) val origin: Vector2,
)

class Transform2DSerializer : KSerializer<Transform2D> {
    override val descriptor: SerialDescriptor = Transform2DSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Transform2D {
        val surrogate = decoder.decodeSerializableValue(Transform2DSurrogate.serializer())
        return Transform2D(
            p_x = surrogate.x,
            p_y = surrogate.y,
            p_origin = surrogate.origin,
        )
    }

    override fun serialize(encoder: Encoder, value: Transform2D) {
        val surrogate = Transform2DSurrogate(x = value.x, y = value.y, origin = value.origin)
        encoder.encodeSerializableValue(Transform2DSurrogate.serializer(), surrogate)
    }
}