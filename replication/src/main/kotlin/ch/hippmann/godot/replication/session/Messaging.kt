package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.core.wire.MessageCodec
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.transport.Channels
import ch.hippmann.godot.replication.transport.Link
import ch.hippmann.godot.replication.transport.TransportLog
import ch.hippmann.godot.replication.transport.TransportFlags
import godot.api.Time

internal fun Link.sendMessage(message: WireMessage) {
    TransportLog.log { "sending ${message.type} to $this" }
    send(Channels.CONTROL, MessageCodec.encode(message), TransportFlags.RELIABLE)
}

internal fun nowMilliseconds(): Long = Time.getTicksMsec()
