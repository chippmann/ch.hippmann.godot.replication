package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.DiscoveredSession
import ch.hippmann.godot.replication.core.wire.DiscoveryAnswer
import ch.hippmann.godot.replication.core.wire.DiscoveryCodec
import ch.hippmann.godot.replication.core.wire.DiscoveryProbe
import ch.hippmann.godot.replication.core.wire.PROTOCOL_VERSION
import godot.api.PacketPeerUDP
import godot.core.Error
import godot.core.PackedByteArray
import godot.global.GD
import kotlinx.coroutines.delay

/** Hosts answer UDP broadcast probes on the discovery port; nothing here touches the ENet mesh. */
class LanDiscovery(private val discoveryPort: Int) {
    private var responder: PacketPeerUDP? = null
    private var answerProvider: (() -> DiscoveryAnswer)? = null

    private var nextBindAttemptMilliseconds = 0L

    /** Binds the discovery port; when it is still held (a master that just left on this machine) [pump] keeps retrying. */
    fun startResponder(answer: () -> DiscoveryAnswer): Boolean {
        answerProvider = answer
        return bindResponder()
    }

    private fun bindResponder(): Boolean {
        val socket = PacketPeerUDP()
        val error = socket.bind(discoveryPort, "*")
        if (error != Error.OK) {
            nextBindAttemptMilliseconds = System.currentTimeMillis() + BIND_RETRY_MILLISECONDS
            return false
        }
        responder = socket
        return true
    }

    fun pump() {
        val answer = answerProvider ?: return
        if (responder == null && System.currentTimeMillis() >= nextBindAttemptMilliseconds) bindResponder()
        val socket = responder ?: return
        while (socket.getAvailablePacketCount() > 0) {
            val bytes = socket.getPacket().toByteArray()
            val address = socket.getPacketIp()
            val port = socket.getPacketPort()
            val probe = DiscoveryCodec.decodeProbe(bytes) ?: continue
            if (probe.protocolVersion != PROTOCOL_VERSION) continue
            socket.setDestAddress(address, port)
            socket.putPacket(PackedByteArray(DiscoveryCodec.encodeAnswer(answer())))
        }
    }

    fun stopResponder() {
        responder?.close()
        responder = null
        answerProvider = null
    }

    suspend fun discover(timeoutMilliseconds: Long): List<DiscoveredSession> {
        val socket = PacketPeerUDP()
        if (socket.bind(0, "*") != Error.OK) return emptyList()
        try {
            socket.setBroadcastEnabled(true)
            val probe = PackedByteArray(DiscoveryCodec.encodeProbe(DiscoveryProbe()))
            for (target in listOf(BROADCAST_ADDRESS, LOOPBACK_ADDRESS)) {
                socket.setDestAddress(target, discoveryPort)
                socket.putPacket(probe)
            }
            val found = LinkedHashMap<String, DiscoveredSession>()
            val deadline = System.currentTimeMillis() + timeoutMilliseconds
            while (System.currentTimeMillis() < deadline) {
                while (socket.getAvailablePacketCount() > 0) {
                    val bytes = socket.getPacket().toByteArray()
                    val address = socket.getPacketIp()
                    val answer = DiscoveryCodec.decodeAnswer(bytes) ?: continue
                    if (answer.protocolVersion != PROTOCOL_VERSION) continue
                    found["$address:${answer.sessionPort}"] = DiscoveredSession(
                        address, answer.sessionPort, answer.lobbyName, answer.playerCount, answer.maximumPlayers, answer.passwordRequired,
                    )
                }
                delay(POLL_MILLISECONDS)
            }
            return found.values.toList()
        } finally {
            socket.close()
        }
    }

    private companion object {
        const val BROADCAST_ADDRESS = "255.255.255.255"
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val POLL_MILLISECONDS = 50L
        const val BIND_RETRY_MILLISECONDS = 1_000L
    }
}
