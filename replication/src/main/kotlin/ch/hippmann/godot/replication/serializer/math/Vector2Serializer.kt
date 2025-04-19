package ch.hippmann.godot.replication.serializer.math

import godot.core.Vector2
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

class Vector2Serializer : KSerializer<Vector2> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(Vector2::class.qualifiedName!!) {
        element<Double>("x")
        element<Double>("y")
    }

    override fun deserialize(decoder: Decoder): Vector2 = decoder.decodeStructure(descriptor) {
        var x = 0.0
        var y = 0.0

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> x = decodeDoubleElement(descriptor, 0)
                1 -> y = decodeDoubleElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index: $index")
            }
        }
        Vector2(x, y)
    }

    override fun serialize(encoder: Encoder, value: Vector2) = encoder.encodeStructure(descriptor) {
        encodeDoubleElement(descriptor, 0, value.x)
        encodeDoubleElement(descriptor, 1, value.y)
    }
}
