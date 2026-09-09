package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.rendezvous.KnockAnswer
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom

/**
 * Everything a member does with the rendezvous service while its session is registered there: the master keeps the
 * registration alive, everybody answers knocks by punching toward the knocker from a fresh socket and listening on it,
 * and the listening socket keeps its public mapping fresh.
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

    /**
     * The listening socket's public endpoint first, then the LAN addresses, for the join request and the registration. A
     * DTLS server socket drops raw datagrams, so an encrypted listener has no observable mapping: callers punch instead.
     */
    suspend fun refreshListenerEndpoints(): List<Endpoint> {
        val listener = runtime.transport.listeningHost ?: return emptyList()
        val public = if (runtime.transport.encrypted) null else observe(listener)
        listenerEndpoints = listOfNotNull(public) + DirectStrategy.localAddresses().map { address -> Endpoint(address, listener.port) }
        return listenerEndpoints
    }

    /** Leaves [knock] for its target and waits for the answer, at most [timeoutMilliseconds]. */
    suspend fun knockAndAwaitAnswer(knock: Knock, timeoutMilliseconds: Long): KnockAnswer? {
        if (!client.knock(code, knock)) return null
        val waitSeconds = (timeoutMilliseconds / 1_000).coerceIn(1, KNOCK_WAIT_SECONDS)
        return withTimeoutOrNull(timeoutMilliseconds) {
            var answer: KnockAnswer? = null
            while (answer == null) answer = client.awaitAnswer(code, knock.token, waitSeconds)
            answer
        }
    }

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

    private suspend fun knocks() = coroutineScope {
        while (true) {
            val knocks = try {
                client.awaitKnocks(code, runtime.localPlayerId.value, KNOCK_WAIT_SECONDS)
            } catch (failure: Exception) {
                TransportLog.log { "rendezvous knock poll failed: ${failure.message}" }
                delay(KNOCK_RETRY_MILLISECONDS)
                emptyList()
            }
            for (knock in knocks) launch { answer(knock) }
        }
    }

    /**
     * Opens a socket for the knocker alone, sends from it toward the knocker's endpoints (or the relay's callee port) so
     * this side's NAT lets the dial in, then listens on it and tells the knocker where it is.
     */
    private suspend fun answer(knock: Knock) {
        if (runtime.membership == null) return
        TransportLog.log { "knock from ${knock.fromMember} toward ${knock.endpoints}${knock.relay?.let { " through relay $it" } ?: ""}" }
        val host = runtime.transport.punchingHost()
        repeat(PUNCH_COUNT) {
            for (endpoint in knock.endpoints) host.socketSend(endpoint.address, endpoint.port, ProbeCodec.encode(knock.token))
            knock.relay?.let { relay -> host.socketSend(relay.address, relay.calleePort, ProbeCodec.encode(knock.token)) }
            delay(PUNCH_INTERVAL_MILLISECONDS)
        }
        val endpoints = if (knock.relay != null) emptyList() else listOfNotNull(observe(host))
        if (knock.relay == null && endpoints.isEmpty()) {
            host.destroy()
            TransportLog.log { "the service never saw the punched socket for ${knock.fromMember}" }
            return
        }
        runtime.transport.expectCaller(host)
        if (!client.answerKnock(code, KnockAnswer(knock.token, endpoints))) TransportLog.log { "the answer to ${knock.fromMember} was refused" }
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
