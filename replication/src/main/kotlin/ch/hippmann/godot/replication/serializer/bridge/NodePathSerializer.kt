package ch.hippmann.godot.replication.serializer.bridge

import godot.core.NodePath
import godot.core.asNodePath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class NodePathSerializer : KSerializer<NodePath> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(NodePath::class.qualifiedName!!) {
        element<String>("nodePath")
    }

    override fun deserialize(decoder: Decoder): NodePath = decoder.decodeString().asNodePath()

    override fun serialize(encoder: Encoder, value: NodePath) = encoder.encodeString(value.path)
}