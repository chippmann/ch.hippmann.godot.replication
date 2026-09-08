package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.codec.Precision
import ch.hippmann.godot.replication.core.codec.Quantization
import ch.hippmann.godot.replication.core.codec.format.ReplicationBinary
import ch.hippmann.godot.replication.core.replication.BooleanCodec
import ch.hippmann.godot.replication.core.replication.ByteArrayCodec
import ch.hippmann.godot.replication.core.replication.DoubleCodec
import ch.hippmann.godot.replication.core.replication.FloatCodec
import ch.hippmann.godot.replication.core.replication.IntCodec
import ch.hippmann.godot.replication.core.replication.LongCodec
import ch.hippmann.godot.replication.core.replication.PropertyCodec
import ch.hippmann.godot.replication.core.replication.SerializerCodec
import ch.hippmann.godot.replication.core.replication.StringCodec
import ch.hippmann.godot.replication.core.replication.TupleCodec
import godot.core.Basis
import godot.core.Color
import godot.core.Quaternion
import godot.core.Transform2D
import godot.core.Transform3D
import godot.core.Vector2
import godot.core.Vector2i
import godot.core.Vector3
import godot.core.Vector3i
import godot.core.Vector4
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

object GodotPropertyCodecs {
    val binary: ReplicationBinary = ReplicationBinary(GodotSerializersModule)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> forClass(type: KClass<T>, quantization: Quantization, precision: Precision): PropertyCodec<T>? = when (type) {
        Boolean::class -> BooleanCodec
        Int::class -> IntCodec
        Long::class -> LongCodec
        Float::class -> FloatCodec(quantization)
        Double::class -> DoubleCodec(quantization, precision)
        String::class -> StringCodec
        ByteArray::class -> ByteArrayCodec
        Vector2::class -> tuple<Vector2>("Vector2", 2, quantization, precision, { doubleArrayOf(it.x, it.y) }, { Vector2(it[0], it[1]) })
        Vector3::class -> tuple<Vector3>("Vector3", 3, quantization, precision, { doubleArrayOf(it.x, it.y, it.z) }, { Vector3(it[0], it[1], it[2]) })
        Vector4::class -> tuple<Vector4>("Vector4", 4, quantization, precision, { doubleArrayOf(it.x, it.y, it.z, it.w) }, { Vector4(it[0], it[1], it[2], it[3]) })
        Quaternion::class -> tuple<Quaternion>("Quaternion", 4, quantization, precision, { doubleArrayOf(it.x, it.y, it.z, it.w) }, { Quaternion(it[0], it[1], it[2], it[3]) })
        Color::class -> tuple<Color>("Color", 4, quantization, precision, { doubleArrayOf(it.r, it.g, it.b, it.a) }, { Color(it[0], it[1], it[2], it[3]) })
        Basis::class -> tuple<Basis>("Basis", 9, quantization, precision, BasisSerializer::components, BasisSerializer::build)
        Transform2D::class -> tuple<Transform2D>("Transform2D", 6, quantization, precision, Transform2DSerializer::components, Transform2DSerializer::build)
        Transform3D::class -> tuple<Transform3D>("Transform3D", 12, quantization, precision, Transform3DSerializer::components, Transform3DSerializer::build)
        Vector2i::class -> SerializerCodec(Vector2iSerializer, binary)
        Vector3i::class -> SerializerCodec(Vector3iSerializer, binary)
        else -> null
    } as PropertyCodec<T>?

    fun <T> forSerializer(serializer: KSerializer<T>): PropertyCodec<T> = SerializerCodec(serializer, binary)

    private fun <T> tuple(
        name: String,
        arity: Int,
        quantization: Quantization,
        precision: Precision,
        components: (T) -> DoubleArray,
        build: (DoubleArray) -> T,
    ): PropertyCodec<T> = object : TupleCodec<T>(name, arity, quantization, precision) {
        override fun components(value: T): DoubleArray = components(value)
        override fun build(components: DoubleArray): T = build(components)
    }
}
