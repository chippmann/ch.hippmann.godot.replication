package ch.hippmann.godot.replication.core.session

import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import kotlinx.serialization.Serializable

@Serializable
public data class MemberRecord(
    val id: PlayerId,
    val profile: PlayerProfile,
    val endpoints: List<Endpoint>,
    val ready: Boolean = false,
    /** PEM of the member's DTLS certificate; empty in a plain session. */
    val certificate: String = "",
)
