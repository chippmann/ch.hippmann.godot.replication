package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.level.LevelState
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.core.session.Epoch
import ch.hippmann.godot.replication.core.session.MemberRecord
import ch.hippmann.godot.replication.core.session.PasswordVerifier
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.session.SessionId
import kotlinx.serialization.Serializable

public const val PROTOCOL_VERSION: Int = 1

@Serializable
public data class JoinRequest(
    val protocolVersion: Int,
    val profile: PlayerProfile,
    val listenPort: Int,
    val localAddresses: List<String>,
) : WireMessage {
    override val type: MessageType get() = MessageType.JOIN_REQUEST
}

@Serializable
public data class Redirect(val master: List<Endpoint>) : WireMessage {
    override val type: MessageType get() = MessageType.REDIRECT
}

@Serializable
public class Challenge(
    public val sessionId: SessionId,
    public val salt: ByteArray,
    public val nonce: ByteArray,
    public val passwordRequired: Boolean,
) : WireMessage {
    override val type: MessageType get() = MessageType.CHALLENGE
}

@Serializable
public class JoinProof(public val proof: ByteArray) : WireMessage {
    override val type: MessageType get() = MessageType.JOIN_PROOF
}

@Serializable
public data class Admitted(
    val playerId: PlayerId,
    val sessionId: SessionId,
    val epoch: Epoch,
    val nextJoinSequence: Int,
    val members: List<MemberRecord>,
    val lobby: LobbyConfiguration,
    val level: LevelState,
    val password: PasswordVerifier?,
) : WireMessage {
    override val type: MessageType get() = MessageType.ADMITTED
}

@Serializable
public enum class RejectReason {
    PROTOCOL_MISMATCH,
    WRONG_PASSWORD,
    FULL,
    LOCKED,
    RETRY,
    NOT_MEMBER,
    KICKED,
}

@Serializable
public data class Rejected(val reason: RejectReason) : WireMessage {
    override val type: MessageType get() = MessageType.REJECTED
}

@Serializable
public data class Hello(val sessionId: SessionId, val playerId: PlayerId, val epoch: Epoch) : WireMessage {
    override val type: MessageType get() = MessageType.HELLO
}

@Serializable
public data class HelloAccepted(val playerId: PlayerId, val epoch: Epoch) : WireMessage {
    override val type: MessageType get() = MessageType.HELLO_ACCEPTED
}
