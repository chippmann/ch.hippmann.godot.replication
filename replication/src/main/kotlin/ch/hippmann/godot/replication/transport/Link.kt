package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.session.PlayerId
import godot.core.Error

object Channels {
    const val CONTROL: Int = 0
    const val STATE: Int = 1
    const val RPC_BASE: Int = 2
}

object TransportFlags {
    const val SEQUENCED: Int = 0
    const val RELIABLE: Int = 1
    const val UNSEQUENCED: Int = 2
}

interface Link {
    var player: PlayerId?
    val remoteAddress: String
    val remotePort: Int
    val roundTripTime: Double

    fun send(channel: Int, bytes: ByteArray, flags: Int): Error

    fun close(graceful: Boolean)
}
