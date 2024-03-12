package ch.hippmann.godot.replication.serializers

import godot.core.Vector3
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

class Vector3Serializer: KSerializer<Vector3> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Vector3") {
        element<Double>("x")
        element<Double>("y")
        element<Double>("z")
    }

    override fun deserialize(decoder: Decoder): Vector3 = decoder.decodeStructure(descriptor) {
        var x = 0.0
        var y = 0.0
        var z = 0.0

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> x = decodeDoubleElement(descriptor, 0)
                1 -> y = decodeDoubleElement(descriptor, 1)
                2 -> z = decodeDoubleElement(descriptor, 2)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index: $index")
            }
        }
        Vector3(x, y, z)
    }

    override fun serialize(encoder: Encoder, value: Vector3) = encoder.encodeStructure(descriptor) {
        encodeDoubleElement(descriptor, 0, value.x)
        encodeDoubleElement(descriptor, 1, value.y)
        encodeDoubleElement(descriptor, 2, value.z)
    }
}
