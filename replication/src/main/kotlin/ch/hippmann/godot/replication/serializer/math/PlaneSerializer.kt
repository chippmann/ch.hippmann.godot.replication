package ch.hippmann.godot.replication.serializer.math

import godot.core.Plane
import godot.core.Vector3
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class PlaneSurrogate(
    @Serializable(Vector3Serializer::class)
    val normal: Vector3,
    val d: Double,
)

class PlaneSerializer : KSerializer<Plane> {
    override val descriptor: SerialDescriptor = PlaneSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Plane {
        val surrogate = decoder.decodeSerializableValue(PlaneSurrogate.serializer())
        return Plane(
            p_normal = surrogate.normal,
            d = surrogate.d,
        )
    }

    override fun serialize(encoder: Encoder, value: Plane) {
        val surrogate = PlaneSurrogate(
            normal = value.normal,
            d = value.d,
        )
        encoder.encodeSerializableValue(PlaneSurrogate.serializer(), surrogate)
    }
}