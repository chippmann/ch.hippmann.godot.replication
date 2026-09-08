package ch.hippmann.godot.replication.core.session

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Equals the Godot peer id. Allocated by the master as the join sequence; 1 is never used so Godot never sees a server. */
@JvmInline
@Serializable
public value class PlayerId(public val value: Int) {
    public companion object {
        public val NONE: PlayerId = PlayerId(0)
        public val FIRST_HOST: PlayerId = PlayerId(2)
    }
}

@JvmInline
@Serializable
public value class Epoch(public val value: Int) {
    public fun next(): Epoch = Epoch(value + 1)

    public companion object {
        public val INITIAL: Epoch = Epoch(1)
    }
}

@JvmInline
@Serializable
public value class SessionId(public val value: Long)

@JvmInline
@Serializable
public value class NetworkId(public val value: Long) {
    public val ownerPart: PlayerId
        get() = PlayerId((value ushr 32).toInt())

    public val counter: Int
        get() = value.toInt()

    public val isScenePlaced: Boolean
        get() = ownerPart == PlayerId.NONE

    public companion object {
        public fun spawned(spawner: PlayerId, counter: Int): NetworkId =
            NetworkId((spawner.value.toLong() shl 32) or (counter.toLong() and 0xFFFF_FFFFL))

        public fun scenePlaced(pathHash: Int): NetworkId = NetworkId(pathHash.toLong() and 0xFFFF_FFFFL)
    }
}
