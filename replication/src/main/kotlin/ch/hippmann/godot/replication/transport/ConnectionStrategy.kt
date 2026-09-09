package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.session.SessionRuntime
import godot.api.IP

/** Whom to reach: the member's id (for knocks through the service) and every endpoint anybody advertised for it. */
internal class ConnectionTarget(val member: PlayerId, val endpoints: List<Endpoint>)

/** How a member reaches another member; strategies are tried in configuration order per member. */
internal interface ConnectionStrategy {
    val name: String

    suspend fun connect(target: ConnectionTarget, session: SessionRuntime, timeoutMilliseconds: Long): EnetLink?
}

internal object DirectStrategy : ConnectionStrategy {
    override val name: String = "direct"

    override suspend fun connect(target: ConnectionTarget, session: SessionRuntime, timeoutMilliseconds: Long): EnetLink? {
        // Behind the internet, an address that does not answer costs a full timeout; keep that short so punching gets its turn.
        val perAttempt = if (session.online != null) minOf(timeoutMilliseconds, ONLINE_ATTEMPT_MILLISECONDS) else timeoutMilliseconds
        for (candidate in target.endpoints) {
            val link = session.transport.dial(candidate.address, candidate.port, perAttempt)
            if (link != null) return link
        }
        return null
    }

    fun localEndpoints(listenPort: Int): List<Endpoint> = localAddresses().map { address -> Endpoint(address, listenPort) }

    /** IPv4 first, loopback last, so members on the same machine still reach each other. */
    fun localAddresses(): List<String> {
        val addresses = IP.getLocalAddresses().toList()
        val ipv4 = addresses.filter { address -> ':' !in address }
        val loopback = ipv4.filter { address -> address.startsWith("127.") }
        return (ipv4 - loopback.toSet()) + loopback
    }

    private const val ONLINE_ATTEMPT_MILLISECONDS = 1_500L
}
