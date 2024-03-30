package ch.hippmann.godot.replication.serializer.math

import godot.core.Basis
import godot.core.Vector3
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class BasisSurrogate(
    @Serializable(with = Vector3Serializer::class)
    val x: Vector3,
    @Serializable(with = Vector3Serializer::class)
    val y: Vector3,
    @Serializable(with = Vector3Serializer::class)
    val z: Vector3,
)

class BasisSerializer : KSerializer<Basis> {
    override val descriptor: SerialDescriptor = BasisSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Basis {
        val surrogate = decoder.decodeSerializableValue(BasisSurrogate.serializer())
        return Basis(
            xAxis = surrogate.x,
            yAxis = surrogate.y,
            zAxis = surrogate.z,
        )
    }

    override fun serialize(encoder: Encoder, value: Basis) {
        val surrogate = BasisSurrogate(
            x = value.x,
            y = value.y,
            z = value.z,
        )
        encoder.encodeSerializableValue(BasisSurrogate.serializer(), surrogate)
    }
}