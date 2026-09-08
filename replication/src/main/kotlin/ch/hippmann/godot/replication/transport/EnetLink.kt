package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.session.PlayerId
import godot.api.ENetPacketPeer
import godot.core.Error
import godot.core.PackedByteArray

class EnetLink(internal val peer: ENetPacketPeer, val outbound: Boolean) : Link {
    val key: Long = peer.objectID.id

    override var player: PlayerId? = null

    /** Set once the peer announced its leave: nothing is sent to it any more while ENet finishes the disconnect. */
    var leaving: Boolean = false

    override val remoteAddress: String = peer.getRemoteAddress()

    override val remotePort: Int = peer.getRemotePort()

    override val roundTripTime: Double
        get() = peer.getStatistic(ENetPacketPeer.PeerStatistic.ROUND_TRIP_TIME)

    val packetLoss: Double
        get() = peer.getStatistic(ENetPacketPeer.PeerStatistic.PACKET_LOSS)

    override fun send(channel: Int, bytes: ByteArray, flags: Int): Error = peer.send(channel, PackedByteArray(bytes), flags)

    override fun close(graceful: Boolean) {
        if (graceful) peer.peerDisconnectLater() else peer.peerDisconnectNow()
    }

    fun configurePeer(linkTimeoutMilliseconds: Int) {
        peer.pingInterval(PING_INTERVAL_MILLISECONDS)
        peer.setTimeout(TIMEOUT_LIMIT, TIMEOUT_MINIMUM_MILLISECONDS, linkTimeoutMilliseconds)
        // ENet drops unreliable packets whenever the round trip fluctuates; on a game loop that stalls per frame it threw away
        // two thirds of the state stream on localhost. Never decelerate: the state stream is rate limited by the tick already.
        peer.throttleConfigure(THROTTLE_INTERVAL_MILLISECONDS, ENetPacketPeer.PACKET_THROTTLE_SCALE.toInt(), 0)
    }

    override fun toString(): String = "EnetLink($remoteAddress:$remotePort, player=${player?.value}, outbound=$outbound)"

    private companion object {
        const val PING_INTERVAL_MILLISECONDS = 500
        const val THROTTLE_INTERVAL_MILLISECONDS = 5_000
        const val TIMEOUT_LIMIT = 8
        const val TIMEOUT_MINIMUM_MILLISECONDS = 2_000
    }
}
