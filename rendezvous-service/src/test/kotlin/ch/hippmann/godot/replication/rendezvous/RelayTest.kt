package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.ProbeCodec
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RelayTest {
    @Test
    fun `datagrams cross the relay in both directions once both sides spoke`() {
        Relay("127.0.0.1", 47800..47810, idleMilliseconds = 60_000).use { relay ->
            val pair = assertNotNull(relay.allocate())
            val caller = DatagramChannel.open().also { it.bind(InetSocketAddress("127.0.0.1", 0)); it.socket().soTimeout = 2_000 }
            val callee = DatagramChannel.open().also { it.bind(InetSocketAddress("127.0.0.1", 0)); it.socket().soTimeout = 2_000 }
            val toCaller = InetSocketAddress("127.0.0.1", pair.callerPort)
            val toCallee = InetSocketAddress("127.0.0.1", pair.calleePort)

            caller.send(ByteBuffer.wrap("hello from caller".toByteArray()), toCaller)
            callee.send(ByteBuffer.wrap("hello from callee".toByteArray()), toCallee)
            assertEquals("hello from callee", receive(caller))
            caller.send(ByteBuffer.wrap("second".toByteArray()), toCaller)
            assertEquals("second", receive(callee))
            caller.close()
            callee.close()
        }
    }

    @Test
    fun `the probe socket records where a token came from`() {
        UdpEndpoint("127.0.0.1", 0).use { endpoint ->
            val client = DatagramChannel.open().also { it.bind(InetSocketAddress("127.0.0.1", 0)) }
            assertNull(endpoint.observed(99))
            client.send(ByteBuffer.wrap(ProbeCodec.encode(99)), InetSocketAddress("127.0.0.1", endpoint.port))
            val deadline = System.currentTimeMillis() + 2_000
            while (endpoint.observed(99) == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
            val observed = assertNotNull(endpoint.observed(99))
            assertEquals((client.localAddress as InetSocketAddress).port, observed.port)
            client.close()
        }
    }

    private fun receive(channel: DatagramChannel): String {
        val buffer = ByteBuffer.allocate(256)
        val packet = java.net.DatagramPacket(buffer.array(), buffer.capacity())
        channel.socket().receive(packet)
        return String(packet.data, 0, packet.length)
    }
}
