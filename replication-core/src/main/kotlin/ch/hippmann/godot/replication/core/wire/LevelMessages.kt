package ch.hippmann.godot.replication.core.wire

import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.core.session.Epoch
import kotlinx.serialization.Serializable

@Serializable
public data class LevelLoad(
    val epoch: Epoch,
    val levelSequence: Int,
    val scenePath: String,
    val policy: LevelPolicy,
) : WireMessage {
    override val type: MessageType get() = MessageType.LEVEL_LOAD
}

@Serializable
public data class LevelLoaded(val levelSequence: Int) : WireMessage {
    override val type: MessageType get() = MessageType.LEVEL_LOADED
}

@Serializable
public data class LevelStart(val epoch: Epoch, val levelSequence: Int) : WireMessage {
    override val type: MessageType get() = MessageType.LEVEL_START
}
