package ch.hippmann.godot.replication.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/** Godot math types on the wire are their components in single precision, without names. */
abstract class FloatTupleSerializer<T>(name: String, private val arity: Int) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(name) {
        repeat(arity) { index -> element<Float>("component$index") }
    }

    abstract fun components(value: T): DoubleArray

    abstract fun build(components: DoubleArray): T

    override fun serialize(encoder: Encoder, value: T) {
        val components = components(value)
        encoder.encodeStructure(descriptor) {
            components.forEachIndexed { index, component -> encodeFloatElement(descriptor, index, component.toFloat()) }
        }
    }

    override fun deserialize(decoder: Decoder): T {
        val components = DoubleArray(arity)
        decoder.decodeStructure(descriptor) {
            if (decodeSequentially()) {
                repeat(arity) { index -> components[index] = decodeFloatElement(descriptor, index).toDouble() }
            } else {
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    components[index] = decodeFloatElement(descriptor, index).toDouble()
                }
            }
        }
        return build(components)
    }
}

abstract class IntTupleSerializer<T>(name: String, private val arity: Int) : KSerializer<T> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(name) {
        repeat(arity) { index -> element<Int>("component$index") }
    }

    abstract fun components(value: T): IntArray

    abstract fun build(components: IntArray): T

    override fun serialize(encoder: Encoder, value: T) {
        val components = components(value)
        encoder.encodeStructure(descriptor) {
            components.forEachIndexed { index, component -> encodeIntElement(descriptor, index, component) }
        }
    }

    override fun deserialize(decoder: Decoder): T {
        val components = IntArray(arity)
        decoder.decodeStructure(descriptor) {
            if (decodeSequentially()) {
                repeat(arity) { index -> components[index] = decodeIntElement(descriptor, index) }
            } else {
                while (true) {
                    val index = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    components[index] = decodeIntElement(descriptor, index)
                }
            }
        }
        return build(components)
    }
}
