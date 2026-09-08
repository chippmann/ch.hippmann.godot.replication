package ch.hippmann.godot.replication.rpc

import ch.hippmann.godot.replication.core.replication.SchemaHash
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.Custom
import ch.hippmann.godot.replication.sync.GodotPropertyCodecs
import ch.hippmann.godot.replication.transport.Channels
import ch.hippmann.godot.replication.transport.Transport
import ch.hippmann.godot.replication.transport.TransportFlags
import kotlinx.serialization.KSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

data class Received<T>(val sender: PlayerId, val payload: T)

class RawCustomMessage(val sender: PlayerId, val typeHash: Int, val bytes: ByteArray)

/** Typed one shot messages between members, keyed by the serial name of the payload type. */
class CustomMessages internal constructor() {
    private val incoming = MutableSharedFlow<RawCustomMessage>(extraBufferCapacity = BUFFER)

    val raw: SharedFlow<RawCustomMessage> = incoming

    fun <T> send(transport: Transport, serializer: KSerializer<T>, payload: T, targets: Set<PlayerId>, reliable: Boolean) {
        val message = Custom(typeHash(serializer), GodotPropertyCodecs.binary.encodeToByteArray(serializer, payload))
        val bytes = ch.hippmann.godot.replication.core.wire.MessageCodec.encode(message)
        val channel = if (reliable) Channels.CONTROL else Channels.STATE
        val flags = if (reliable) TransportFlags.RELIABLE else TransportFlags.SEQUENCED
        for (target in targets) transport.send(target, channel, bytes, flags)
    }

    fun <T> messages(serializer: KSerializer<T>): Flow<Received<T>> {
        val hash = typeHash(serializer)
        return incoming.filter { message -> message.typeHash == hash }
            .map { message -> Received(message.sender, GodotPropertyCodecs.binary.decodeFromByteArray(serializer, message.bytes)) }
    }

    fun onMessage(sender: PlayerId, message: Custom) {
        incoming.tryEmit(RawCustomMessage(sender, message.typeHash, message.bytes))
    }

    private fun typeHash(serializer: KSerializer<*>): Int = SchemaHash.ofString(serializer.descriptor.serialName)

    private companion object {
        const val BUFFER = 256
    }
}
