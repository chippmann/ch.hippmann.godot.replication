package ch.hippmann.godot.replication.serializer.math

import godot.core.Projection
import godot.core.Vector4
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class ProjectionSurrogate(
    @Serializable(Vector4Serializer::class) val x: Vector4,
    @Serializable(Vector4Serializer::class) val y: Vector4,
    @Serializable(Vector4Serializer::class) val z: Vector4,
    @Serializable(Vector4Serializer::class) val w: Vector4,
)

class ProjectionSerializer : KSerializer<Projection> {
    override val descriptor: SerialDescriptor = ProjectionSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Projection {
        val surrogate = decoder.decodeSerializableValue(ProjectionSurrogate.serializer())
        return Projection(
            p_x = surrogate.x,
            p_y = surrogate.y,
            p_z = surrogate.z,
            p_w = surrogate.w,
        )
    }

    override fun serialize(encoder: Encoder, value: Projection) {
        val surrogate = ProjectionSurrogate(
            x = value.x,
            y = value.y,
            z = value.z,
            w = value.w,
        )
        encoder.encodeSerializableValue(ProjectionSurrogate.serializer(), surrogate)
    }
}