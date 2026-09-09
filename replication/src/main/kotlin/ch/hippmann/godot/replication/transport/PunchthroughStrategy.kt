package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.session.SessionRuntime
import java.security.SecureRandom

/**
 * Hole punching through the rendezvous service: a fresh socket learns its public mapping from the service and knocks;
 * the target opens a socket of its own, sends toward that mapping, which opens its NAT, and answers with where that
 * socket is reachable; then this side dials it. Cone NATs on both ends make this work; a symmetric NAT on either end
 * does not, and [RelayStrategy] takes over.
 */
internal object PunchthroughStrategy : ConnectionStrategy {
    override val name: String = "punchthrough"
    private val random = SecureRandom()

    override suspend fun connect(target: ConnectionTarget, session: SessionRuntime, timeoutMilliseconds: Long): EnetLink? {
        val online = session.online ?: return null
        val host = session.transport.punchingHost()
        val token = random.nextLong()
        val observed = online.observe(host, token)
        if (observed == null) {
            host.destroy()
            TransportLog.log { "punchthrough: the service never saw the probe for ${target.member.value}" }
            return null
        }
        val mine = (listOf(observed) + DirectStrategy.localAddresses().map { address -> Endpoint(address, host.localPort()) }).distinct()
        val answer = online.knockAndAwaitAnswer(Knock(session.localPlayerId.value, target.member.value, mine, token), timeoutMilliseconds)
        val endpoint = answer?.endpoints?.firstOrNull()
        if (endpoint == null) {
            host.destroy()
            TransportLog.log { "punchthrough: ${target.member.value} did not answer with a punched endpoint" }
            return null
        }
        session.transport.secureDialer(host, target.certificate)
        return session.transport.dial(endpoint.address, endpoint.port, timeoutMilliseconds, host)
    }
}
