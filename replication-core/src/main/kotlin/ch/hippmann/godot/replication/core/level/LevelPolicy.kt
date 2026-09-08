package ch.hippmann.godot.replication.core.level

import kotlinx.serialization.Serializable

@Serializable
public sealed interface LevelPolicy {
    /** The master starts the level once every member reported it loaded, or after [stragglerTimeoutMilliseconds]. */
    @Serializable
    public data class WaitForAll(val stragglerTimeoutMilliseconds: Long = 20_000) : LevelPolicy

    @Serializable
    public data object StartWhenLoaded : LevelPolicy
}
