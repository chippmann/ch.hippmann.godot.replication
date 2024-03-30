package ch.hippmann.godot.replication.serializer.bridge

import godot.core.StringName
import godot.core.asStringName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class StringNameSerializer: KSerializer<StringName> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(StringName::class.qualifiedName!!) {
        element<String>("nodePath")
    }

    override fun deserialize(decoder: Decoder): StringName = decoder.decodeString().asStringName()

    override fun serialize(encoder: Encoder, value: StringName) = encoder.encodeString(value.toString())
}