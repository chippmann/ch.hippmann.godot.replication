package ch.hippmann.godot.replication.core.replication

public sealed interface SyncMode {
    public data object OnChange : SyncMode

    /** Resent every tick divisible by [rate] while changing, then [idleAfterTicks] more ticks. */
    public data class Continuous(val rate: Int, val idleAfterTicks: Int = 10) : SyncMode
}

public data class PropertySchema(
    val index: Int,
    val name: String,
    val codecId: String,
    val reliable: Boolean,
    val mode: SyncMode,
    val interpolated: Boolean,
) {
    val bit: Long
        get() = 1L shl index
}

public data class ClassSchema(val className: String, val properties: List<PropertySchema>) {
    init {
        require(properties.size <= MAXIMUM_PROPERTIES) { "$className declares ${properties.size} synced properties, the limit is $MAXIMUM_PROPERTIES" }
    }

    val hash: Int = SchemaHash.compute(this)

    val reliableMask: Long = properties.filter { property -> property.reliable }.fold(0L) { mask, property -> mask or property.bit }

    val fullMask: Long = properties.fold(0L) { mask, property -> mask or property.bit }

    public companion object {
        public const val MAXIMUM_PROPERTIES: Int = 64
    }
}

public object SchemaHash {
    private const val OFFSET_BASIS = 0x811C9DC5.toInt()
    private const val PRIME = 0x01000193

    public fun compute(schema: ClassSchema): Int {
        var hash = fnv1a(OFFSET_BASIS, schema.className)
        for (property in schema.properties) {
            hash = fnv1a(hash, "${property.index}:${property.name}:${property.codecId}:${property.reliable}:${property.mode}:${property.interpolated}")
        }
        return hash
    }

    public fun ofString(text: String): Int = fnv1a(OFFSET_BASIS, text)

    private fun fnv1a(seed: Int, text: String): Int {
        var hash = seed
        for (byte in text.encodeToByteArray()) {
            hash = (hash xor (byte.toInt() and 0xFF)) * PRIME
        }
        return hash
    }
}
