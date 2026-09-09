package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.session.SessionRuntime
import kotlinx.coroutines.delay
import java.security.SecureRandom

/**
 * Hole punching through the rendezvous service: a fresh outbound socket learns its public mapping from the service, the
 * target is told about it and sends toward it from its listening socket, which opens the target's NAT, then this side dials.
 * Cone NATs on both ends make this work; a symmetric NAT on either end does not, and [RelayStrategy] takes over.
 */
internal object PunchthroughStrategy : ConnectionStrategy {
    override val name: String = "punchthrough"
    private val random = SecureRandom()

    override suspend fun connect(target: ConnectionTarget, session: SessionRuntime, timeoutMilliseconds: Long): EnetLink? {
        val online = session.online ?: return null
        val publicEndpoints = if (session.configuration.punchPrivateAddresses) target.endpoints else target.endpoints.filterNot { endpoint -> isPrivate(endpoint.address) }
        if (publicEndpoints.isEmpty()) return null
        for (endpoint in publicEndpoints) {
            val host = session.transport.outboundHost()
            val token = random.nextLong()
            val observed = online.observe(host, token)
            if (observed == null) {
                host.destroy()
                TransportLog.log { "punchthrough: the service never saw the probe for ${target.member.value}" }
                return null
            }
            val mine = listOf(observed) + DirectStrategy.localAddresses().map { address -> Endpoint(address, host.localPort()) }
            online.knock(Knock(session.localPlayerId.value, target.member.value, mine, token))
            delay(SETTLE_MILLISECONDS)
            val link = session.transport.dial(endpoint.address, endpoint.port, timeoutMilliseconds, host)
            if (link != null) return link
        }
        return null
    }

    fun isPrivate(address: String): Boolean =
        address.startsWith("10.") || address.startsWith("192.168.") || address.startsWith("127.") ||
            address.startsWith("169.254.") || Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(address)

    private const val SETTLE_MILLISECONDS = 400L
}
