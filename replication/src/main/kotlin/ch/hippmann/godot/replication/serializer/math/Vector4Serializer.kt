package ch.hippmann.godot.replication.serializer.math

import godot.core.Vector4
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

class Vector4Serializer : KSerializer<Vector4> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(Vector4::class.qualifiedName!!) {
        element<Double>("x")
        element<Double>("y")
        element<Double>("z")
        element<Double>("w")
    }

    override fun deserialize(decoder: Decoder): Vector4 = decoder.decodeStructure(descriptor) {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var w = 0.0

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> x = decodeDoubleElement(descriptor, 0)
                1 -> y = decodeDoubleElement(descriptor, 1)
                2 -> z = decodeDoubleElement(descriptor, 2)
                3 -> w = decodeDoubleElement(descriptor, 3)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index: $index")
            }
        }
        Vector4(x, y, z, w)
    }

    override fun serialize(encoder: Encoder, value: Vector4) = encoder.encodeStructure(descriptor) {
        encodeDoubleElement(descriptor, 0, value.x)
        encodeDoubleElement(descriptor, 1, value.y)
        encodeDoubleElement(descriptor, 2, value.z)
        encodeDoubleElement(descriptor, 3, value.w)
    }
}
