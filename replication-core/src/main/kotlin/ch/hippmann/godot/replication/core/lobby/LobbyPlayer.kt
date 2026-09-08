package ch.hippmann.godot.replication.core.lobby

import ch.hippmann.godot.replication.core.session.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class LobbyPlayer(
    val id: PlayerId,
    val profile: PlayerProfile,
    val ready: Boolean = false,
)
