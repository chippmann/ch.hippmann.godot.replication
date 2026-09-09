package ch.hippmann.godot.replication.transport

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.session.SessionRuntime
import java.security.SecureRandom

/**
 * The service forwards raw datagrams between two of its ports; the target sends toward its port first so the relay learns
 * where it lives, answers, and this side dials the other port as if it were the target itself. Works behind any NAT.
 */
internal object RelayStrategy : ConnectionStrategy {
    override val name: String = "relay"
    private val random = SecureRandom()

    override suspend fun connect(target: ConnectionTarget, session: SessionRuntime, timeoutMilliseconds: Long): EnetLink? {
        val online = session.online ?: return null
        val allocation = online.allocateRelay(target.member.value) ?: return null
        TransportLog.log { "relay through ${allocation.address}:${allocation.callerPort} for ${target.member.value}" }
        val knock = Knock(session.localPlayerId.value, target.member.value, emptyList(), random.nextLong(), relay = allocation)
        if (online.knockAndAwaitAnswer(knock, timeoutMilliseconds) == null) {
            TransportLog.log { "relay: ${target.member.value} did not answer" }
            return null
        }
        return session.transport.dial(allocation.address, allocation.callerPort, timeoutMilliseconds, session.transport.outboundHost(target.certificate))
    }
}
