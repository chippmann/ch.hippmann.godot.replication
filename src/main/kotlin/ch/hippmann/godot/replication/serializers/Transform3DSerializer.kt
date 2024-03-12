package ch.hippmann.godot.replication.serializers

import godot.core.Basis
import godot.core.Transform3D
import godot.core.Vector3
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class Transform3DSurrogate(
        @Serializable(with = BasisSerializer::class)
        val basis: Basis,
        @Serializable(with = Vector3Serializer::class)
        val origin: Vector3
)

class Transform3DSerializer: KSerializer<Transform3D> {
    override val descriptor: SerialDescriptor = Transform3DSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Transform3D {
        val surrogate = decoder.decodeSerializableValue(Transform3DSurrogate.serializer())
        return Transform3D().apply {
            basis = surrogate.basis
            origin = surrogate.origin
        }
    }

    override fun serialize(encoder: Encoder, value: Transform3D) {
        val surrogate = Transform3DSurrogate(basis = value.basis, origin = value.origin)
        encoder.encodeSerializableValue(Transform3DSurrogate.serializer(), surrogate)
    }
}
