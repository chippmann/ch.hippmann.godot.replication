package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.serializer.deserialize
import ch.hippmann.godot.replication.serializer.serialize
import godot.api.Node
import kotlin.reflect.KMutableProperty0

typealias SerializedData = String

@DslMarker
annotation class SyncConfigDslMarker

typealias SyncConfigs = Map<String, SyncConfig>

@SyncConfigDslMarker
class SyncConfigDsl {
    @PublishedApi
    internal val configs: MutableMap<String, SyncConfig> = mutableMapOf()

    /**
     * **Requirement**: {OWNER} is the owner of the property you want to synchronize
     */
    inline fun <reified OWNER : Node, reified PROPERTY_TYPE> OWNER.property(
        property: KMutableProperty0<PROPERTY_TYPE>,
        noinline serializer: PROPERTY_TYPE.() -> SerializedData = { this.serialize() },
        noinline deserializer: (SerializedData) -> PROPERTY_TYPE = { serializedData -> serializedData.deserialize() },
        config: PropertyConfig<PROPERTY_TYPE>.() -> Unit = {},
    ) {
        val propertyConfig = PropertyConfig<PROPERTY_TYPE>()
        config(propertyConfig)

        val fqName = "${this::class.qualifiedName}::${property.name}"

        require(configs[fqName] == null) {
            "Trying to set config of property $fqName a second time. This is not allowed. Probably you did not call this function on the owner of the property but on the node you configured the synchronisation. Previous config: ${configs[fqName]}"
        }

        var lastSyncState: PROPERTY_TYPE? = null

        configs[fqName] = SyncConfig(
            tick = propertyConfig.tick,
            syncMethod = propertyConfig.syncMethod,
            syncOnSpawn = propertyConfig.syncOnSpawn,
            syncOnTick = propertyConfig.syncOnTick,
            getter = { serializer(property.get()) },
            setter = { data -> property.set(deserializer(data)) },
            shouldSendUpdate = {
                val current = property.get()
                val result = lastSyncState?.let { propertyConfig.shouldSendUpdate(current, it) } ?: true
                lastSyncState = current
                result
            }
        )
    }

    class PropertyConfig<PROPERTY_TYPE> {
        var tick: Long = 16
        var syncMethod: SyncConfig.SyncMethod = SyncConfig.SyncMethod.RELIABLE
        var syncOnSpawn: Boolean = true
        var syncOnTick: Boolean = true
        var shouldSendUpdate: (current: PROPERTY_TYPE, fromLastSync: PROPERTY_TYPE) -> Boolean = { current, fromLastSync -> current != fromLastSync }
    }
}

fun Node.syncConfig(block: SyncConfigDsl.() -> Unit): SyncConfigs {
    val syncConfigDsl = SyncConfigDsl()
    block(syncConfigDsl)
    return syncConfigDsl.configs
}


data class SyncConfig(
    val tick: Long = 16,
    val syncMethod: SyncMethod = SyncMethod.RELIABLE,
    val syncChannel: SyncChannel = SyncChannel.CHANNEL_0,
    val syncOnSpawn: Boolean = true,
    val syncOnTick: Boolean = true,
    val getter: () -> SerializedData,
    val setter: (SerializedData) -> Unit,
    val shouldSendUpdate: () -> Boolean,
) {
    enum class SyncMethod {
        RELIABLE,
        UNRELIABLE,
        UNRELIABLE_ORDERED,
    }

    enum class SyncChannel {
        CHANNEL_0,
        CHANNEL_1,
        CHANNEL_2,
        CHANNEL_3,
        CHANNEL_4,
        CHANNEL_5,
        CHANNEL_6,
        CHANNEL_7,
        CHANNEL_8,
        CHANNEL_9,
    }
}

typealias SyncConfigDto = SerializedData

//TODO: Kotlin 1.8.0: add Replicated context
fun SyncConfigs.serializeSpawnData(): SerializedData {
    val spawnData: Map<String, SyncConfigDto> = filterValues { config -> config.syncOnSpawn }
        .map { (fqName, config) ->
            fqName to config.getter()
        }
        .toMap()
    return spawnData.serialize()
}

fun SyncConfigs.applySpawnData(data: SerializedData) {
    data
        .deserialize<Map<String, SyncConfigDto>>()
        .forEach { (fqName, data) ->
            get(fqName)?.setter?.invoke(data)
        }
}

//TODO: Kotlin 1.8.0: add Replicated context
fun SyncConfig.serializeSyncData(): SerializedData {
    return getter()
}

//TODO: Kotlin 1.8.0: add Replicated context
fun SyncConfig.applySyncData(data: SerializedData) {
    setter(data)
}
