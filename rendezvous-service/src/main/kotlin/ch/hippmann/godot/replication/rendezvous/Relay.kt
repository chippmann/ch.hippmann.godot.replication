package ch.hippmann.godot.replication.rendezvous

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * A pair of sockets per link: whatever arrives on the caller's port leaves through the callee's port toward the callee and the
 * other way round. The first datagram on each port fixes who that side is. ENet on both ends only ever sees the relay.
 */
class Relay(private val bindAddress: String, private val ports: IntRange, private val idleMilliseconds: Long, private val clock: () -> Long = System::currentTimeMillis) : AutoCloseable {
    class Pair(val callerChannel: DatagramChannel, val calleeChannel: DatagramChannel) {
        @Volatile var caller: InetSocketAddress? = null
        @Volatile var callee: InetSocketAddress? = null
        @Volatile var lastTrafficMilliseconds: Long = 0
        val callerPort: Int get() = (callerChannel.localAddress as InetSocketAddress).port
        val calleePort: Int get() = (calleeChannel.localAddress as InetSocketAddress).port
    }

    private class Attachment(val pair: Pair, val fromCaller: Boolean)

    private val selector: Selector = Selector.open()
    private val pairs = ConcurrentLinkedQueue<Pair>()
    private val pendingRegistrations = ConcurrentLinkedQueue<Pair>()
    private val usedPorts = HashSet<Int>()

    @Volatile
    private var running = true

    private val worker = thread(name = "rendezvous-relay", isDaemon = true) { forwardLoop() }

    /** Two free ports from the range, or null when the range is exhausted. */
    fun allocate(): Pair? {
        val caller = openChannel() ?: return null
        val callee = openChannel() ?: run { release(caller); return null }
        val pair = Pair(caller, callee).also { it.lastTrafficMilliseconds = clock() }
        pairs += pair
        pendingRegistrations += pair
        selector.wakeup()
        return pair
    }

    val activePairs: Int
        get() = pairs.size

    private fun openChannel(): DatagramChannel? {
        synchronized(usedPorts) {
            for (port in ports) {
                if (port in usedPorts) continue
                val channel = DatagramChannel.open()
                try {
                    channel.bind(InetSocketAddress(bindAddress, port))
                } catch (unavailable: Exception) {
                    channel.close()
                    continue
                }
                channel.configureBlocking(false)
                usedPorts += port
                return channel
            }
        }
        return null
    }

    private fun release(channel: DatagramChannel) {
        synchronized(usedPorts) { usedPorts -= (channel.localAddress as InetSocketAddress).port }
        channel.close()
    }

    private fun forwardLoop() {
        val buffer = ByteBuffer.allocate(2048)
        while (running) {
            while (true) {
                val pair = pendingRegistrations.poll() ?: break
                pair.callerChannel.register(selector, SelectionKey.OP_READ, Attachment(pair, fromCaller = true))
                pair.calleeChannel.register(selector, SelectionKey.OP_READ, Attachment(pair, fromCaller = false))
            }
            selector.select(1_000)
            val keys = selector.selectedKeys().iterator()
            while (keys.hasNext()) {
                val key = keys.next()
                keys.remove()
                val attachment = key.attachment() as Attachment
                forward(attachment.pair, attachment.fromCaller, buffer)
            }
            expire()
        }
    }

    private fun forward(pair: Pair, fromCaller: Boolean, buffer: ByteBuffer) {
        val inbound = if (fromCaller) pair.callerChannel else pair.calleeChannel
        val outbound = if (fromCaller) pair.calleeChannel else pair.callerChannel
        while (true) {
            buffer.clear()
            val source = inbound.receive(buffer) as? InetSocketAddress ?: return
            buffer.flip()
            if (fromCaller) pair.caller = source else pair.callee = source
            pair.lastTrafficMilliseconds = clock()
            val target = (if (fromCaller) pair.callee else pair.caller) ?: continue
            outbound.send(buffer, target)
        }
    }

    private fun expire() {
        val now = clock()
        val iterator = pairs.iterator()
        while (iterator.hasNext()) {
            val pair = iterator.next()
            if (now - pair.lastTrafficMilliseconds <= idleMilliseconds) continue
            iterator.remove()
            release(pair.callerChannel)
            release(pair.calleeChannel)
        }
    }

    override fun close() {
        running = false
        selector.wakeup()
        worker.join(2_000)
        pairs.forEach { pair -> release(pair.callerChannel); release(pair.calleeChannel) }
        selector.close()
    }
}
