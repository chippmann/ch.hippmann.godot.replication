package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.ObservedEndpoint
import ch.hippmann.godot.replication.core.rendezvous.ProbeCodec
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * The probe socket: whatever sends a probe is recorded under its token with the address the datagram came from, which is the
 * sender's public mapping when it sits behind a NAT. Probes repeated every few seconds also keep that mapping alive.
 */
class UdpEndpoint(bindAddress: String, port: Int, private val clock: () -> Long = System::currentTimeMillis) : AutoCloseable {
    private class Observation(val endpoint: ObservedEndpoint, val seenMilliseconds: Long)

    private val channel: DatagramChannel = DatagramChannel.open().also { channel -> channel.bind(InetSocketAddress(bindAddress, port)) }
    private val observations = ConcurrentHashMap<Long, Observation>()

    @Volatile
    private var running = true

    val port: Int = (channel.localAddress as InetSocketAddress).port

    private val worker = thread(name = "rendezvous-udp", isDaemon = true) { receiveLoop() }

    fun observed(token: Long): ObservedEndpoint? = observations[token]?.endpoint

    private fun receiveLoop() {
        val buffer = ByteBuffer.allocate(64)
        while (running) {
            buffer.clear()
            val source = try {
                channel.receive(buffer) as? InetSocketAddress ?: continue
            } catch (closed: Exception) {
                if (running) continue else return
            }
            buffer.flip()
            val bytes = ByteArray(buffer.remaining()).also(buffer::get)
            val token = ProbeCodec.decode(bytes) ?: continue
            observations[token] = Observation(ObservedEndpoint(source.address.hostAddress, source.port), clock())
            forget(clock() - OBSERVATION_TIME_TO_LIVE_MILLISECONDS)
        }
    }

    private fun forget(olderThanMilliseconds: Long) {
        observations.values.removeIf { observation -> observation.seenMilliseconds < olderThanMilliseconds }
    }

    override fun close() {
        running = false
        channel.close()
        worker.join(1_000)
    }

    private companion object {
        const val OBSERVATION_TIME_TO_LIVE_MILLISECONDS = 120_000L
    }
}
