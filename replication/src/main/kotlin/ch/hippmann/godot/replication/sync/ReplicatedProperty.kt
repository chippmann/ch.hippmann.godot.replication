package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.replication.InterpolationBuffer
import ch.hippmann.godot.replication.core.replication.Interpolator
import ch.hippmann.godot.replication.core.replication.PropertyCodec
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class ReplicatedProperty<T>(
    val name: String,
    val options: SyncedOptions<T>,
    val codec: PropertyCodec<T>,
) {
    internal lateinit var replica: Replica
    internal var index: Int = -1
    internal var lastChangedTick: Int = Int.MIN_VALUE
    internal val buffer: InterpolationBuffer<T>? = if (options.interpolated) InterpolationBuffer() else null
    internal val interpolator: Interpolator<T>? = options.interpolator

    abstract fun currentValue(): T

    /** Writes a value that arrived from the owner; interpolated properties buffer it instead of applying it. */
    abstract fun applyRemote(value: T, receiveTimeMilliseconds: Long)

    /** Engine bindings compare the live engine value with the last sent one; delegates track writes directly. */
    abstract fun pollChanged(): Boolean

    abstract fun applyInterpolated(renderTimeMilliseconds: Long, maximumExtrapolationMilliseconds: Long)

    /** The configured delay, or more when this stream's arrivals were gappy lately. */
    internal fun renderTime(nowMilliseconds: Long): Long =
        nowMilliseconds - maxOf(replica.interpolationDelayMilliseconds, buffer?.recommendedDelayMilliseconds ?: 0L)

    fun write(writer: ByteWriter): Unit = codec.write(writer, currentValue())

    fun read(reader: ByteReader): T = codec.read(reader)

    protected fun sample(renderTimeMilliseconds: Long, maximumExtrapolationMilliseconds: Long): T? {
        val buffer = buffer ?: return null
        val interpolator = interpolator ?: return buffer.latest
        return buffer.sample(renderTimeMilliseconds, interpolator, maximumExtrapolationMilliseconds)
    }
}

class SyncedProperty<T>(name: String, options: SyncedOptions<T>, codec: PropertyCodec<T>, initial: T) :
    ReplicatedProperty<T>(name, options, codec), ReadWriteProperty<Any?, T> {
    private var value: T = initial
    private var sampledFrame = -1L
    private var sampledValue: T = initial

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val buffer = buffer
        if (buffer == null || buffer.isEmpty || replica.isOwnedLocally) return value
        if (sampledFrame != FrameClock.frame) {
            sampledFrame = FrameClock.frame
            sampledValue = sample(renderTime(FrameClock.nowMilliseconds), replica.maximumExtrapolationMilliseconds) ?: value
        }
        return sampledValue
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (this.value == value) return
        this.value = value
        replica.markDirty(this)
    }

    override fun currentValue(): T = value

    override fun applyRemote(value: T, receiveTimeMilliseconds: Long) {
        val old = this.value
        this.value = value
        buffer?.push(receiveTimeMilliseconds, value)
        if (old != value) options.onRemoteChange?.invoke(old, value)
    }

    override fun pollChanged(): Boolean = false

    override fun applyInterpolated(renderTimeMilliseconds: Long, maximumExtrapolationMilliseconds: Long) {
    }
}

class EnginePropertyBinding<T>(
    name: String,
    options: SyncedOptions<T>,
    codec: PropertyCodec<T>,
    private val getter: () -> T,
    private val setter: (T) -> Unit,
) : ReplicatedProperty<T>(name, options, codec) {
    private var lastSent: T? = null
    private var hasLastSent = false

    override fun currentValue(): T = getter()

    override fun applyRemote(value: T, receiveTimeMilliseconds: Long) {
        if (buffer != null) buffer.push(receiveTimeMilliseconds, value) else setter(value)
    }

    override fun pollChanged(): Boolean {
        val current = getter()
        if (hasLastSent && current == lastSent) return false
        lastSent = current
        hasLastSent = true
        return true
    }

    override fun applyInterpolated(renderTimeMilliseconds: Long, maximumExtrapolationMilliseconds: Long) {
        sample(renderTimeMilliseconds, maximumExtrapolationMilliseconds)?.let(setter)
    }
}

class SpawnDataProperty<T>(val name: String, val codec: PropertyCodec<T>) {
    internal var value: T? = null
    internal var assigned = false

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        check(assigned) { "Spawn data '$name' is only available once the node was spawned through Network.spawn" }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    internal fun assign(value: Any?) {
        @Suppress("UNCHECKED_CAST")
        this.value = value as T
        assigned = true
    }
}
