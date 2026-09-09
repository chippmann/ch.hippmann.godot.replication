package ch.hippmann.godot.replication.sync

/**
 * Replicates a plain `var` of a node class without a delegate; the `replication-processor` KSP module generates the
 * binding, which polls the property once per tick like `synced(::property)` does. The class must be top level, extend
 * `godot.api.Node`, and the property must be a non private `var`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Synced(
    val reliable: Boolean = true,
    /** Resent every [rate] ticks while changing and [idleAfterTicks] ticks after; false sends on change only. */
    val continuous: Boolean = false,
    val rate: Int = 0,
    val idleAfterTicks: Int = 10,
    val interpolate: Boolean = false,
    /** Sends floating point components as half floats. */
    val half: Boolean = false,
    /** Sends floating point components as multiples of this step; 0 keeps them as they are. */
    val step: Double = 0.0,
    val doublePrecision: Boolean = false,
)

/** Implemented by the generated `<ClassName>SyncedProperties` objects; the library looks them up by name. */
interface GeneratedSyncedProperties<in N> {
    fun bind(node: N)
}
