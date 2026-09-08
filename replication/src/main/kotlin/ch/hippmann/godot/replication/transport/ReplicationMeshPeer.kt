package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.MessageType
import godot.annotation.Register
import godot.annotation.Script
import godot.api.MultiplayerPeer
import godot.api.MultiplayerPeerExtension
import godot.core.Error
import godot.core.PackedByteArray

/**
 * Presents the mesh to Godot's SceneMultiplayer. Godot RPC packets travel on ENet channel 2 plus the transfer
 * channel with a leading 0xF0 plus channel byte; the library's own packets never pass through here.
 * SceneMultiplayer asks for the peer, channel and mode of a packet before it fetches it, so those describe the
 * head of the queue.
 */
@Script
class ReplicationMeshPeer : MultiplayerPeerExtension() {
    private class RpcPacket(val from: Int, val channel: Int, val mode: MultiplayerPeer.TransferMode, val bytes: ByteArray)

    private val incoming = ArrayDeque<RpcPacket>()
    private var targetPeer = 0
    private var requestedChannel = 0
    private var requestedMode = MultiplayerPeer.TransferMode.RELIABLE
    private var refusing = false

    var transport: Transport? = null
    var localPlayerId: PlayerId = PlayerId.NONE
    var status: MultiplayerPeer.ConnectionStatus = MultiplayerPeer.ConnectionStatus.DISCONNECTED

    private val announced = HashSet<Int>()
    private val pendingLeaves = ArrayDeque<Int>()

    fun announcePeer(player: PlayerId) {
        if (announced.add(player.value)) peerConnected.emit(player.value.toLong())
    }

    /** SceneMultiplayer drops packets of unknown peers, so a leave waits until the peer's queued packets are consumed. */
    fun announcePeerLeft(player: PlayerId) {
        if (player.value !in announced) return
        if (incoming.none { packet -> packet.from == player.value }) emitLeft(player.value) else pendingLeaves.addLast(player.value)
    }

    private fun emitLeft(player: Int) {
        if (announced.remove(player)) peerDisconnected.emit(player.toLong())
    }

    private fun flushPendingLeaves() {
        while (pendingLeaves.isNotEmpty()) {
            val player = pendingLeaves.first()
            if (incoming.any { packet -> packet.from == player }) return
            pendingLeaves.removeFirst()
            emitLeft(player)
        }
    }

    fun enqueue(from: PlayerId, channel: Int, bytes: ByteArray) {
        incoming.addLast(RpcPacket(from.value, channel, MultiplayerPeer.TransferMode.RELIABLE, bytes))
    }

    @Register
    override fun _getAvailablePacketCount(): Int {
        if (incoming.isEmpty()) flushPendingLeaves()
        return incoming.size
    }

    @Register
    override fun _getMaxPacketSize(): Int = MAXIMUM_PACKET_SIZE

    @Register
    override fun _getPacketScript(): PackedByteArray {
        val packet = incoming.removeFirst()
        return PackedByteArray(packet.bytes.copyOfRange(1, packet.bytes.size))
    }

    @Register
    override fun _putPacketScript(buffer: PackedByteArray): Error {
        val transport = transport ?: return Error.UNCONFIGURED
        val payload = buffer.toByteArray()
        val bytes = ByteArray(payload.size + 1)
        bytes[0] = (MessageType.RPC_PACKET_BASE + requestedChannel).toByte()
        payload.copyInto(bytes, 1)
        val channel = Channels.RPC_BASE + requestedChannel
        val flags = when (requestedMode) {
            MultiplayerPeer.TransferMode.RELIABLE -> TransportFlags.RELIABLE
            MultiplayerPeer.TransferMode.UNRELIABLE -> TransportFlags.UNSEQUENCED
            MultiplayerPeer.TransferMode.UNRELIABLE_ORDERED -> TransportFlags.SEQUENCED
        }
        when {
            targetPeer == 0 -> transport.broadcast(channel, bytes, flags)
            targetPeer > 0 -> if (!transport.send(PlayerId(targetPeer), channel, bytes, flags)) return Error.UNAVAILABLE
            else -> transport.broadcast(channel, bytes, flags, except = PlayerId(-targetPeer))
        }
        return Error.OK
    }

    @Register
    override fun _getPacketChannel(): Int = incoming.firstOrNull()?.channel ?: 0

    @Register
    override fun _getPacketMode(): MultiplayerPeer.TransferMode = incoming.firstOrNull()?.mode ?: MultiplayerPeer.TransferMode.RELIABLE

    @Register
    override fun _setTransferChannel(channel: Int) {
        requestedChannel = channel
    }

    @Register
    override fun _getTransferChannel(): Int = requestedChannel

    @Register
    override fun _setTransferMode(mode: MultiplayerPeer.TransferMode) {
        requestedMode = mode
    }

    @Register
    override fun _getTransferMode(): MultiplayerPeer.TransferMode = requestedMode

    @Register
    override fun _setTargetPeer(peer: Int) {
        targetPeer = peer
    }

    @Register
    override fun _getPacketPeer(): Int = incoming.firstOrNull()?.from ?: 0

    @Register
    override fun _isServer(): Boolean = false

    @Register
    override fun _poll() {
        transport?.pump()
    }

    @Register
    override fun _close() {
        incoming.clear()
        announced.clear()
        pendingLeaves.clear()
        status = MultiplayerPeer.ConnectionStatus.DISCONNECTED
    }

    @Register
    override fun _disconnectPeer(peer: Int, force: Boolean) {
        val transport = transport ?: return
        val link = transport.linkFor(PlayerId(peer)) ?: return
        transport.close(link, graceful = !force)
    }

    @Register
    override fun _getUniqueId(): Int = localPlayerId.value

    @Register
    override fun _setRefuseNewConnections(enable: Boolean) {
        refusing = enable
    }

    @Register
    override fun _isRefusingNewConnections(): Boolean = refusing

    @Register
    override fun _isServerRelaySupported(): Boolean = false

    @Register
    override fun _getConnectionStatus(): MultiplayerPeer.ConnectionStatus = status

    companion object {
        const val MAXIMUM_PACKET_SIZE: Int = 1 shl 16
    }
}
