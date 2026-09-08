package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.session.Endpoint
import godot.api.IP

/** How a member reaches another member; strategies are tried in configuration order per member. */
interface ConnectionStrategy {
    val name: String

    fun advertise(listenPort: Int): List<Endpoint>

    suspend fun connect(candidates: List<Endpoint>, transport: Transport, timeoutMilliseconds: Long): EnetLink?
}

object DirectStrategy : ConnectionStrategy {
    override val name: String = "direct"

    override fun advertise(listenPort: Int): List<Endpoint> = localAddresses().map { address -> Endpoint(address, listenPort) }

    override suspend fun connect(candidates: List<Endpoint>, transport: Transport, timeoutMilliseconds: Long): EnetLink? {
        for (candidate in candidates) {
            val link = transport.dial(candidate.address, candidate.port, timeoutMilliseconds)
            if (link != null) return link
        }
        return null
    }

    /** IPv4 first, loopback last, so members on the same machine still reach each other. */
    fun localAddresses(): List<String> {
        val addresses = IP.getLocalAddresses().toList()
        val ipv4 = addresses.filter { address -> ':' !in address }
        val loopback = ipv4.filter { address -> address.startsWith("127.") }
        return (ipv4 - loopback.toSet()) + loopback
    }
}
