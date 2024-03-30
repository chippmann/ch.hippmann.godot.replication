package ch.hippmann.godot.replication.serializer.math

import godot.core.Rect2i
import godot.core.Vector2i
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class Rect2iSurrogate(
    @Serializable(Vector2iSerializer::class) val position: Vector2i,
    @Serializable(Vector2iSerializer::class) val size: Vector2i,
)

class Rect2iSerializer : KSerializer<Rect2i> {
    override val descriptor: SerialDescriptor = Rect2iSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Rect2i {
        val surrogate = decoder.decodeSerializableValue(Rect2iSurrogate.serializer())
        return Rect2i(
            p_position = surrogate.position,
            p_size = surrogate.size,
        )
    }

    override fun serialize(encoder: Encoder, value: Rect2i) {
        val surrogate = Rect2iSurrogate(
            position = value.position,
            size = value.size,
        )
        encoder.encodeSerializableValue(Rect2iSurrogate.serializer(), surrogate)
    }
}