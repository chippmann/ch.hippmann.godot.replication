package ch.hippmann.godot.replication.core.level

import ch.hippmann.godot.replication.core.session.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class LevelState(
    val sequence: Int,
    val scenePath: String?,
    val policy: LevelPolicy,
    val started: Boolean,
    val loaded: Set<PlayerId>,
) {
    public companion object {
        public val NONE: LevelState = LevelState(0, null, LevelPolicy.StartWhenLoaded, false, emptySet())
    }
}
