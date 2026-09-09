package ch.hippmann.godot.replication.transport

import godot.api.ENetConnection
import godot.api.ENetPacketPeer
import godot.core.Error
import godot.core.PackedByteArray

interface EnetEventHandler {
    fun onConnect(peer: ENetPacketPeer)

    fun onDisconnect(peer: ENetPacketPeer)

    fun onReceive(peer: ENetPacketPeer, bytes: ByteArray)
}

/**
 * One ENetConnection. Godot refuses `connect_to_host` on a connection that already has a peer, so a process keeps
 * one bound listening host for inbound links and one unbound host per outbound link.
 */
class EnetHost private constructor(
    private val connection: ENetConnection,
    val port: Int,
    private val channels: Int,
) {
    private var active = true

    fun connect(address: String, port: Int): ENetPacketPeer? = connection.connectToHost(address, port, channels)

    /** A raw datagram from this host's socket; it opens NAT mappings and tells the rendezvous service where we are. */
    fun socketSend(address: String, port: Int, bytes: ByteArray) {
        if (active) connection.socketSend(address, port, PackedByteArray(bytes))
    }

    /** The port the socket actually got, also for an outbound host that only sent something so far. */
    fun localPort(): Int = if (port != 0) port else connection.getLocalPort()

    /** Drains every pending ENet event; called at least once per frame from the main thread. */
    fun service(handler: EnetEventHandler) {
        while (active) {
            val event = connection.service(0)
            val type = (event[0] as Number).toInt()
            when (type) {
                EVENT_CONNECT -> handler.onConnect(event[1] as ENetPacketPeer)
                EVENT_DISCONNECT -> handler.onDisconnect(event[1] as ENetPacketPeer)
                EVENT_RECEIVE -> {
                    val peer = event[1] as ENetPacketPeer
                    handler.onReceive(peer, peer.getPacket().toByteArray())
                }
                else -> return
            }
        }
    }

    fun flush() {
        if (active) connection.flush()
    }

    fun refuseNewConnections(refuse: Boolean): Unit = connection.refuseNewConnections(refuse)

    fun destroy() {
        if (!active) return
        active = false
        connection.destroy()
    }

    companion object {
        private val EVENT_CONNECT = ENetConnection.EventType.CONNECT.value.toInt()
        private val EVENT_DISCONNECT = ENetConnection.EventType.DISCONNECT.value.toInt()
        private val EVENT_RECEIVE = ENetConnection.EventType.RECEIVE.value.toInt()

        fun bind(port: Int, maximumPeers: Int, channels: Int): EnetHost {
            val connection = ENetConnection()
            val error = connection.createHostBound("*", port, maximumPeers, channels)
            check(error == Error.OK) { "Could not bind UDP port $port: $error" }
            return EnetHost(connection, connection.getLocalPort(), channels)
        }

        fun outbound(channels: Int): EnetHost {
            val connection = ENetConnection()
            val error = connection.createHost(1, channels)
            check(error == Error.OK) { "Could not create an outbound ENet host: $error" }
            return EnetHost(connection, 0, channels)
        }
    }
}
