package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.core.wire.WireMessage

interface TransportListener {
    fun onInboundLink(link: EnetLink)

    fun onLinkClosed(link: EnetLink)

    fun onControlMessage(link: EnetLink, message: WireMessage)

    fun onStatePacket(link: EnetLink, type: MessageType, bytes: ByteArray)

    fun onRpcPacket(link: EnetLink, channel: Int, bytes: ByteArray)
}
