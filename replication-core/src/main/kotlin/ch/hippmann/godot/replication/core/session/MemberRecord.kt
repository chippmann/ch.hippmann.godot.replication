package ch.hippmann.godot.replication.core.session

import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import kotlinx.serialization.Serializable

@Serializable
public data class MemberRecord(
    val id: PlayerId,
    val profile: PlayerProfile,
    val endpoints: List<Endpoint>,
    val ready: Boolean = false,
)
