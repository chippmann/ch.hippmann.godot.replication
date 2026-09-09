package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.NetworkConfiguration
import ch.hippmann.godot.replication.core.codec.CodecException
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.MessageCodec
import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.core.diagnostics.Counters
import ch.hippmann.godot.replication.core.simulation.DelayQueue
import ch.hippmann.godot.replication.core.simulation.NetworkConditions
import ch.hippmann.godot.replication.diagnostics.NetworkStatistics
import godot.api.ENetPacketPeer
import godot.global.GD
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Accepts inbound links on the listening host, dials outbound ones and routes every packet by its first byte.
 * Coroutine resumptions and events triggered by packets run through [defer] after the ENet service loop, so user
 * code never re-enters the transport from inside a handler.
 */
class Transport(
    private val configuration: NetworkConfiguration,
    private val listener: TransportListener,
) : EnetEventHandler {
    private val linksByKey = HashMap<Long, EnetLink>()
    private val linksByPlayer = HashMap<Int, EnetLink>()
    private val outboundHosts = HashMap<Long, EnetHost>()
    private val pendingDials = HashMap<Long, CompletableDeferred<EnetLink>>()
    private val packetsAwaitingIdentity = HashMap<Long, MutableList<ByteArray>>()
    private val deferredActions = ArrayDeque<() -> Unit>()
    private var pumping = false
    private var outboundDelay: DelayQueue<QueuedPacket>? = null
    private var inboundDelay: DelayQueue<QueuedPacket>? = null

    private class QueuedPacket(val link: EnetLink, val channel: Int, val bytes: ByteArray, val flags: Int)

    val counters = Counters()

    var simulation: NetworkConditions? = null
        set(value) {
            field = value
            outboundDelay = value?.let { DelayQueue(it) }
            inboundDelay = value?.let { DelayQueue(it) }
        }

    var listeningHost: EnetHost? = null
        private set

    val links: Collection<EnetLink>
        get() = linksByKey.values

    private val channels: Int
        get() = Channels.RPC_BASE + configuration.rpcChannels

    fun bind(port: Int, maximumPeers: Int): EnetHost {
        check(listeningHost == null) { "The transport is already bound" }
        return EnetHost.bind(port, maximumPeers, channels).also { listeningHost = it }
    }

    fun pump() {
        if (!pumping) {
            pumping = true
            try {
                listeningHost?.service(this)
                for (host in outboundHosts.values.toList()) host.service(this)
                drainSimulation()
            } finally {
                pumping = false
            }
        }
        runDeferred()
    }

    private fun drainSimulation() {
        val now = System.currentTimeMillis()
        outboundDelay?.drain(now) { packet -> if (packet.link.key in linksByKey) packet.link.send(packet.channel, packet.bytes, packet.flags) }
        inboundDelay?.drain(now) { packet -> if (packet.link.key in linksByKey) route(packet.link, packet.bytes) }
    }

    private fun transmit(link: EnetLink, channel: Int, bytes: ByteArray, flags: Int) {
        counters.add(NetworkStatistics.PACKETS_OUT)
        counters.add(NetworkStatistics.BYTES_OUT, bytes.size.toLong())
        val delay = outboundDelay
        if (delay == null) {
            link.send(channel, bytes, flags)
        } else {
            delay.offer(System.currentTimeMillis(), QueuedPacket(link, channel, bytes, flags), droppable = flags != TransportFlags.RELIABLE)
        }
    }

    fun flush() {
        listeningHost?.flush()
        outboundHosts.values.forEach(EnetHost::flush)
    }

    fun defer(action: () -> Unit) {
        deferredActions.addLast(action)
    }

    fun runDeferred() {
        while (deferredActions.isNotEmpty()) deferredActions.removeFirst()()
    }

    fun outboundHost(): EnetHost = EnetHost.outbound(channels)

    suspend fun dial(address: String, port: Int, timeoutMilliseconds: Long, host: EnetHost = outboundHost()): EnetLink? {
        val peer = host.connect(address, port)
        if (peer == null) {
            host.destroy()
            return null
        }
        val key = peer.objectID.id
        outboundHosts[key] = host
        val deferred = CompletableDeferred<EnetLink>()
        pendingDials[key] = deferred
        TransportLog.log { "dialing $address:$port (peer $key)" }
        val link = withTimeoutOrNull(timeoutMilliseconds) { deferred.await() }
        if (link == null) {
            TransportLog.log { "dial to $address:$port timed out" }
            pendingDials.remove(key)
            outboundHosts.remove(key)?.destroy()
        }
        return link
    }

    fun linkFor(player: PlayerId): EnetLink? = linksByPlayer[player.value]

    val connectedPlayers: Set<PlayerId>
        get() = linksByPlayer.values.filter { link -> !link.leaving }.mapNotNull { link -> link.player }.toSet()

    fun identify(link: EnetLink, player: PlayerId) {
        link.player = player
        linksByPlayer[player.value] = link
        packetsAwaitingIdentity.remove(link.key)?.forEach { bytes -> route(link, bytes) }
    }

    fun send(player: PlayerId, channel: Int, bytes: ByteArray, flags: Int): Boolean {
        val link = linksByPlayer[player.value] ?: return false
        if (link.leaving) return false
        transmit(link, channel, bytes, flags)
        return true
    }

    fun broadcast(channel: Int, bytes: ByteArray, flags: Int, except: PlayerId? = null) {
        for (link in linksByPlayer.values) {
            if (link.player != except && !link.leaving) transmit(link, channel, bytes, flags)
        }
    }

    fun sendMessage(player: PlayerId, message: WireMessage): Boolean =
        send(player, Channels.CONTROL, MessageCodec.encode(message), TransportFlags.RELIABLE)

    fun broadcastMessage(message: WireMessage, except: PlayerId? = null): Unit =
        broadcast(Channels.CONTROL, MessageCodec.encode(message), TransportFlags.RELIABLE, except)

    fun close(link: EnetLink, graceful: Boolean) {
        if (link.key !in linksByKey) return
        link.close(graceful)
        if (!graceful) forget(link)
    }

    fun shutdown() {
        for (link in linksByKey.values.toList()) link.close(graceful = true)
        flush()
        listeningHost?.destroy()
        listeningHost = null
        outboundHosts.values.forEach(EnetHost::destroy)
        outboundHosts.clear()
        linksByKey.clear()
        linksByPlayer.clear()
        packetsAwaitingIdentity.clear()
        pendingDials.values.forEach { pending -> pending.cancel() }
        pendingDials.clear()
        outboundDelay?.clear()
        inboundDelay?.clear()
    }

    override fun onConnect(peer: ENetPacketPeer) {
        val pending = pendingDials.remove(peer.objectID.id)
        val link = EnetLink(peer, outbound = pending != null)
        TransportLog.log { "connected $link" }
        link.configurePeer(configuration.linkTimeoutMilliseconds)
        linksByKey[link.key] = link
        if (pending != null) defer { pending.complete(link) } else defer { listener.onInboundLink(link) }
    }

    override fun onDisconnect(peer: ENetPacketPeer) {
        val key = peer.objectID.id
        pendingDials.remove(key)?.let { pending -> defer { pending.cancel() } }
        val link = linksByKey[key] ?: return
        TransportLog.log { "disconnected $link" }
        forget(link)
        defer { listener.onLinkClosed(link) }
    }

    override fun onReceive(peer: ENetPacketPeer, bytes: ByteArray) {
        val link = linksByKey[peer.objectID.id] ?: return
        if (bytes.isEmpty()) return
        counters.add(NetworkStatistics.PACKETS_IN)
        counters.add(NetworkStatistics.BYTES_IN, bytes.size.toLong())
        val delay = inboundDelay
        if (delay == null) {
            route(link, bytes)
        } else {
            val firstByte = bytes[0].toInt() and 0xFF
            delay.offer(System.currentTimeMillis(), QueuedPacket(link, 0, bytes, 0), droppable = MessageType.fromId(firstByte) == MessageType.STATE_DELTA)
        }
    }

    private fun route(link: EnetLink, bytes: ByteArray) {
        val firstByte = bytes[0].toInt() and 0xFF
        val type = MessageType.fromId(firstByte)
        val isHandshake = type != null && type.id <= MessageType.HELLO_ACCEPTED.id
        when {
            // Anything but the handshake can follow the admission on the same link before the joiner processed it.
            link.player == null && !isHandshake -> packetsAwaitingIdentity.getOrPut(link.key) { mutableListOf() }.add(bytes)
            MessageType.isRpcPacket(firstByte) -> listener.onRpcPacket(link, firstByte - MessageType.RPC_PACKET_BASE, bytes)
            else -> routeLibraryPacket(link, firstByte, bytes)
        }
    }

    private fun routeLibraryPacket(link: EnetLink, firstByte: Int, bytes: ByteArray) {
        val type = MessageType.fromId(firstByte)
        if (type == null) {
            GD.printErr("Replication: dropping packet with unknown type $firstByte from $link")
            return
        }
        TransportLog.log { "received $type (${bytes.size} bytes) from $link" }
        if (MessageCodec.isControlMessage(type)) {
            val message = try {
                MessageCodec.decode(bytes)
            } catch (exception: CodecException) {
                GD.printErr("Replication: dropping malformed $type from $link: ${exception.message}")
                return
            }
            listener.onControlMessage(link, message)
        } else {
            listener.onStatePacket(link, type, bytes)
        }
    }

    private fun forget(link: EnetLink) {
        linksByKey.remove(link.key)
        packetsAwaitingIdentity.remove(link.key)
        outboundHosts.remove(link.key)?.destroy()
        val player = link.player
        if (player != null && linksByPlayer[player.value] === link) linksByPlayer.remove(player.value)
    }
}
