package ch.hippmann.godot.replication.serializer.math

import godot.core.AABB
import godot.core.Vector3
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class AABBSurrogate(
    @Serializable(with = Vector3Serializer::class)
    val position: Vector3,
    @Serializable(with = Vector3Serializer::class)
    val size: Vector3,
)

class AABBSerializer : KSerializer<AABB> {
    override val descriptor: SerialDescriptor = AABBSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): AABB {
        val surrogate = decoder.decodeSerializableValue(AABBSurrogate.serializer())
        return AABB(
            position =  surrogate.position,
            size = surrogate.size
        )
    }

    override fun serialize(encoder: Encoder, value: AABB) {
        val surrogate = AABBSurrogate(
            position = value.position,
            size = value.size,
        )
        encoder.encodeSerializableValue(AABBSurrogate.serializer(), surrogate)
    }
}
