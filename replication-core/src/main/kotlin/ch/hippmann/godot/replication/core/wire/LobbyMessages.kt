package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.session.Epoch
import ch.hippmann.godot.replication.core.session.PlayerId
import kotlinx.serialization.Serializable

@Serializable
public data class LobbyUpdate(val epoch: Epoch, val configuration: LobbyConfiguration) : WireMessage {
    override val type: MessageType get() = MessageType.LOBBY_UPDATE
}

@Serializable
public data class PlayerUpdate(val player: PlayerId, val profile: PlayerProfile, val ready: Boolean) : WireMessage {
    override val type: MessageType get() = MessageType.PLAYER_UPDATE
}

@Serializable
public sealed interface LobbyCommandPayload {
    @Serializable
    public data class UpdateLobby(val configuration: LobbyConfiguration) : LobbyCommandPayload

    @Serializable
    public data class LoadLevel(val scenePath: String, val policy: LevelPolicy) : LobbyCommandPayload
}

@Serializable
public data class LobbyCommand(val payload: LobbyCommandPayload) : WireMessage {
    override val type: MessageType get() = MessageType.LOBBY_COMMAND
}
