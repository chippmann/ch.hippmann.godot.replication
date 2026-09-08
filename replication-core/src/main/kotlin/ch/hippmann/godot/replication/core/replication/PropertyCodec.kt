package ch.hippmann.godot.replication.core.replication

import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.codec.Precision
import ch.hippmann.godot.replication.core.codec.Quantization
import ch.hippmann.godot.replication.core.codec.format.ReplicationBinary
import kotlinx.serialization.KSerializer

/** Encodes one synced property; [codecId] takes part in the schema hash so both sides agree on the layout. */
public interface PropertyCodec<T> {
    public val codecId: String

    public fun write(writer: ByteWriter, value: T)

    public fun read(reader: ByteReader): T
}

public object BooleanCodec : PropertyCodec<Boolean> {
    override val codecId: String = "boolean"
    override fun write(writer: ByteWriter, value: Boolean): Unit = writer.writeBoolean(value)
    override fun read(reader: ByteReader): Boolean = reader.readBoolean()
}

public object IntCodec : PropertyCodec<Int> {
    override val codecId: String = "int"
    override fun write(writer: ByteWriter, value: Int): Unit = writer.writeZigZagInt(value)
    override fun read(reader: ByteReader): Int = reader.readZigZagInt()
}

public object LongCodec : PropertyCodec<Long> {
    override val codecId: String = "long"
    override fun write(writer: ByteWriter, value: Long): Unit = writer.writeZigZagLong(value)
    override fun read(reader: ByteReader): Long = reader.readZigZagLong()
}

public object StringCodec : PropertyCodec<String> {
    override val codecId: String = "string"
    override fun write(writer: ByteWriter, value: String): Unit = writer.writeString(value)
    override fun read(reader: ByteReader): String = reader.readString()
}

public object ByteArrayCodec : PropertyCodec<ByteArray> {
    override val codecId: String = "bytes"
    override fun write(writer: ByteWriter, value: ByteArray): Unit = writer.writeBytes(value)
    override fun read(reader: ByteReader): ByteArray = reader.readBytes()
}

public class FloatCodec(private val quantization: Quantization) : PropertyCodec<Float> {
    override val codecId: String = "float/${quantization.id}"
    override fun write(writer: ByteWriter, value: Float): Unit = quantization.write(writer, value.toDouble(), Precision.Single)
    override fun read(reader: ByteReader): Float = quantization.read(reader, Precision.Single).toFloat()
}

public class DoubleCodec(private val quantization: Quantization, private val precision: Precision) : PropertyCodec<Double> {
    override val codecId: String = "double/${quantization.id}/${precision.ordinal}"
    override fun write(writer: ByteWriter, value: Double): Unit = quantization.write(writer, value, precision)
    override fun read(reader: ByteReader): Double = quantization.read(reader, precision)
}

/** Fixed arity numeric types such as vectors and transforms: every component goes through the same quantizer. */
public abstract class TupleCodec<T>(
    private val name: String,
    private val arity: Int,
    private val quantization: Quantization,
    private val precision: Precision,
) : PropertyCodec<T> {
    override val codecId: String = "$name/$arity/${quantization.id}/${precision.ordinal}"

    public abstract fun components(value: T): DoubleArray

    public abstract fun build(components: DoubleArray): T

    override fun write(writer: ByteWriter, value: T) {
        val components = components(value)
        for (component in components) quantization.write(writer, component, precision)
    }

    override fun read(reader: ByteReader): T = build(DoubleArray(arity) { quantization.read(reader, precision) })
}

public class SerializerCodec<T>(private val serializer: KSerializer<T>, private val format: ReplicationBinary) : PropertyCodec<T> {
    override val codecId: String = "serializable/${serializer.descriptor.serialName}"
    override fun write(writer: ByteWriter, value: T): Unit = format.encodeTo(writer, serializer, value)
    override fun read(reader: ByteReader): T = format.decodeFrom(reader, serializer)
}
