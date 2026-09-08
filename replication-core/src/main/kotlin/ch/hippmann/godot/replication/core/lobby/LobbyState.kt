package ch.hippmann.godot.replication.core.lobby

import ch.hippmann.godot.replication.core.session.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class LobbyState(
    val configuration: LobbyConfiguration,
    val players: List<LobbyPlayer>,
    val master: PlayerId,
) {
    val allReady: Boolean
        get() = players.isNotEmpty() && players.all { player -> player.ready }

    public fun player(id: PlayerId): LobbyPlayer? = players.firstOrNull { player -> player.id == id }

    public companion object {
        public val EMPTY: LobbyState = LobbyState(LobbyConfiguration(name = ""), emptyList(), PlayerId.NONE)
    }
}
