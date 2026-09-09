package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.rendezvous.OnlineSessionInfo
import ch.hippmann.godot.replication.core.rendezvous.ProbeCodec
import ch.hippmann.godot.replication.core.rendezvous.RelayAllocation
import ch.hippmann.godot.replication.core.rendezvous.RelayRequest
import ch.hippmann.godot.replication.core.rendezvous.SessionHeartbeat
import ch.hippmann.godot.replication.core.session.Endpoint
import ch.hippmann.godot.replication.session.SessionRuntime
import ch.hippmann.godot.replication.transport.DirectStrategy
import ch.hippmann.godot.replication.transport.EnetHost
import ch.hippmann.godot.replication.transport.TransportLog
import godot.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.security.SecureRandom

/**
 * Everything a member does with the rendezvous service while its session is registered there: the master keeps the
 * registration alive, everybody answers knocks by opening its NAT toward the knocker, and the listening socket keeps its
 * public mapping fresh.
 */
internal class OnlineSession(
    private val runtime: SessionRuntime,
    val client: RendezvousClient,
    var code: String,
    var secret: String?,
    val udpAddress: String,
    val udpPort: Int,
) {
    private val random = SecureRandom()
    private var job: Job? = null

    var listenerEndpoints: List<Endpoint> = emptyList()
        private set

    val info: OnlineSessionInfo?
        get() = secret?.let { OnlineSessionInfo(client.baseUrl, code, it) }

    fun start() {
        job = runtime.manager.launch {
            supervisorScope {
                launch { heartbeats() }
                launch { knocks() }
                launch { keepalive() }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Sends probes from [host] and asks the service where they came from: that is the socket's public endpoint. */
    suspend fun observe(host: EnetHost, token: Long = random.nextLong()): Endpoint? {
        repeat(PROBE_COUNT) {
            host.socketSend(udpAddress, udpPort, ProbeCodec.encode(token))
            delay(PROBE_INTERVAL_MILLISECONDS)
        }
        repeat(OBSERVE_ATTEMPTS) {
            client.observe(token)?.let { observed -> return Endpoint(observed.address, observed.port) }
            delay(OBSERVE_INTERVAL_MILLISECONDS)
        }
        return null
    }

    /** The listening socket's public endpoint first, then the LAN addresses, for the join request and the registration. */
    suspend fun refreshListenerEndpoints(): List<Endpoint> {
        val listener = runtime.transport.listeningHost ?: return emptyList()
        val public = observe(listener)
        listenerEndpoints = listOfNotNull(public) + DirectStrategy.localAddresses().map { address -> Endpoint(address, listener.port) }
        return listenerEndpoints
    }

    suspend fun knock(knock: Knock): Boolean = client.knock(code, knock)

    suspend fun allocateRelay(toMember: Int): RelayAllocation? =
        client.allocateRelay(code, RelayRequest(runtime.localPlayerId.value, toMember))

    private suspend fun heartbeats() {
        while (true) {
            delay(HEARTBEAT_INTERVAL_MILLISECONDS)
            val secret = secret ?: continue
            if (!runtime.isMaster) continue
            val membership = runtime.membership ?: continue
            val heartbeat = SessionHeartbeat(secret, membership.members.size, runtime.localPlayerId.value, listenerEndpoints, runtime.certificatePem)
            if (client.heartbeat(code, heartbeat) == null) TransportLog.log { "rendezvous heartbeat for $code was refused" }
        }
    }

    private suspend fun knocks() {
        while (true) {
            val knocks = try {
                client.awaitKnocks(code, runtime.localPlayerId.value, KNOCK_WAIT_SECONDS)
            } catch (failure: Exception) {
                TransportLog.log { "rendezvous knock poll failed: ${failure.message}" }
                delay(KNOCK_RETRY_MILLISECONDS)
                emptyList()
            }
            for (knock in knocks) answer(knock)
        }
    }

    /** Opens this side's NAT toward the knocker, or toward the relay when the knocker asked for one. */
    private suspend fun answer(knock: Knock) {
        val listener = runtime.transport.listeningHost ?: return
        TransportLog.log { "knock from ${knock.fromMember} toward ${knock.endpoints}${knock.relay?.let { " through relay $it" } ?: ""}" }
        repeat(PUNCH_COUNT) {
            for (endpoint in knock.endpoints) listener.socketSend(endpoint.address, endpoint.port, ProbeCodec.encode(knock.token))
            knock.relay?.let { relay -> listener.socketSend(relay.address, relay.calleePort, ProbeCodec.encode(knock.token)) }
            delay(PUNCH_INTERVAL_MILLISECONDS)
        }
    }

    private suspend fun keepalive() {
        while (true) {
            delay(KEEPALIVE_INTERVAL_MILLISECONDS)
            refreshListenerEndpoints()
        }
    }

    companion object {
        const val PROBE_COUNT = 3
        const val PROBE_INTERVAL_MILLISECONDS = 120L
        const val OBSERVE_ATTEMPTS = 10
        const val OBSERVE_INTERVAL_MILLISECONDS = 200L
        const val PUNCH_COUNT = 5
        const val PUNCH_INTERVAL_MILLISECONDS = 150L
        const val HEARTBEAT_INTERVAL_MILLISECONDS = 10_000L
        const val KEEPALIVE_INTERVAL_MILLISECONDS = 20_000L
        const val KNOCK_WAIT_SECONDS = 20L
        const val KNOCK_RETRY_MILLISECONDS = 2_000L
    }
}
