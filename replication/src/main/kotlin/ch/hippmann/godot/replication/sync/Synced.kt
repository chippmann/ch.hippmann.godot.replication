package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.replication.PropertyCodec
import godot.api.Node
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

class SyncedDelegateProvider<T> @PublishedApi internal constructor(private val initial: T, private val options: SyncedOptions<T>, private val codec: PropertyCodec<T>) {
    operator fun provideDelegate(thisRef: Node, property: KProperty<*>): ReadWriteProperty<Any?, T> {
        val synced = SyncedProperty(property.name, options, codec, initial)
        NodeRegistry.replicaFor(thisRef).register(synced)
        return synced
    }
}

class SpawnDataDelegateProvider<T> @PublishedApi internal constructor(private val codec: PropertyCodec<T>) {
    operator fun provideDelegate(thisRef: Node, property: KProperty<*>): SpawnDataProperty<T> {
        val spawnData = SpawnDataProperty(property.name, codec)
        NodeRegistry.replicaFor(thisRef).spawnDataProperties += spawnData
        return spawnData
    }
}

/** Declares a replicated field: `var health by synced(100) { reliable(); onChange() }`. */
inline fun <reified T : Any> synced(initial: T, noinline configure: SyncedOptions<T>.() -> Unit = {}): SyncedDelegateProvider<T> {
    val options = SyncedOptions<T>().apply(configure)
    if (options.interpolated && options.interpolator == null) options.interpolator = GodotInterpolators.forClass(T::class)
    return SyncedDelegateProvider(initial, options, options.codec ?: defaultCodec<T>(options))
}

/** Replicates an engine property polled once per tick: `synced(::globalTransform) { unreliable(); continuous(); interpolate() }`. */
inline fun <reified T : Any> Node.synced(property: KMutableProperty0<T>, noinline configure: SyncedOptions<T>.() -> Unit = {}): EnginePropertyBinding<T> =
    synced(property.name, { property.get() }, { value -> property.set(value) }, configure)

inline fun <reified T : Any> Node.synced(
    name: String,
    noinline getter: () -> T,
    noinline setter: (T) -> Unit,
    noinline configure: SyncedOptions<T>.() -> Unit = {},
): EnginePropertyBinding<T> {
    val options = SyncedOptions<T>().apply(configure)
    if (options.interpolated && options.interpolator == null) options.interpolator = GodotInterpolators.forClass(T::class)
    val binding = EnginePropertyBinding(name, options, options.codec ?: defaultCodec<T>(options), getter, setter)
    NodeRegistry.replicaFor(this).register(binding)
    return binding
}

/** Data handed to `Network.spawn`, available on every peer before `_ready`: `val loadout by spawnData<Loadout>()`. */
inline fun <reified T : Any> spawnData(): SpawnDataDelegateProvider<T> =
    SpawnDataDelegateProvider(defaultCodec<T>(SyncedOptions()))

inline fun <reified T : Any> defaultCodec(options: SyncedOptions<T>): PropertyCodec<T> =
    GodotPropertyCodecs.forClass(T::class, options.quantization, options.precision) ?: try {
        GodotPropertyCodecs.forSerializer(serializer<T>())
    } catch (exception: SerializationException) {
        throw IllegalArgumentException("${T::class.simpleName} needs @Serializable or an explicit codec(...) to be synced", exception)
    }
