package ch.hippmann.godot.replication.serializer.math

import godot.core.Quaternion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
private data class QuaternionSurrogate(
    val x: Double,
    val y: Double,
    val z: Double,
    val w: Double,
)

class QuaternionSerializer : KSerializer<Quaternion> {
    override val descriptor: SerialDescriptor = QuaternionSurrogate.serializer().descriptor

    override fun deserialize(decoder: Decoder): Quaternion {
        val surrogate = decoder.decodeSerializableValue(QuaternionSurrogate.serializer())
        return Quaternion(
            x = surrogate.x,
            y = surrogate.y,
            z = surrogate.z,
            w = surrogate.w,
        )
    }

    override fun serialize(encoder: Encoder, value: Quaternion) {
        val surrogate = QuaternionSurrogate(
            x = value.x,
            y = value.y,
            z = value.z,
            w = value.w,
        )
        encoder.encodeSerializableValue(QuaternionSurrogate.serializer(), surrogate)
    }
}