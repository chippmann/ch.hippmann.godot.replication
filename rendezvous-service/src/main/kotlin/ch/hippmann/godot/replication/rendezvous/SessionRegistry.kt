package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.rendezvous.PublishedSession
import ch.hippmann.godot.replication.core.rendezvous.RendezvousProtocol
import ch.hippmann.godot.replication.core.rendezvous.SessionHeartbeat
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class RegisteredSession(
    val code: String,
    val hostSecret: String,
    @Volatile var registration: SessionRegistration,
    @Volatile var lastSeenMilliseconds: Long,
) {
    val knocks = ConcurrentHashMap<Int, Channel<Knock>>()

    fun mailbox(member: Int): Channel<Knock> = knocks.getOrPut(member) { Channel(Channel.UNLIMITED) }

    fun published(): PublishedSession = PublishedSession(
        code = code,
        lobbyName = registration.lobbyName,
        playerCount = registration.playerCount,
        maximumPlayers = registration.maximumPlayers,
        passwordRequired = registration.passwordRequired,
        encrypted = registration.encrypted,
        masterId = registration.masterId,
        masterCertificate = registration.masterCertificate,
        masterEndpoints = registration.masterEndpoints,
    )
}

/** Sessions by code with a time to live the host refreshes; knocks wait in per member mailboxes for the long poll. */
class SessionRegistry(private val timeToLiveMilliseconds: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private val sessions = ConcurrentHashMap<String, RegisteredSession>()
    private val random = SecureRandom()

    fun register(registration: SessionRegistration): RegisteredSession {
        expire()
        while (true) {
            val code = randomCode()
            val session = RegisteredSession(code, randomSecret(), registration, clock())
            if (sessions.putIfAbsent(code, session) == null) return session
        }
    }

    fun find(code: String): RegisteredSession? {
        expire()
        return sessions[code.uppercase()]
    }

    fun heartbeat(code: String, heartbeat: SessionHeartbeat): RegisteredSession? {
        val session = find(code) ?: return null
        if (session.hostSecret != heartbeat.hostSecret) return null
        session.registration = session.registration.copy(
            playerCount = heartbeat.playerCount,
            masterId = heartbeat.masterId,
            masterEndpoints = heartbeat.masterEndpoints,
            masterCertificate = heartbeat.masterCertificate,
        )
        session.lastSeenMilliseconds = clock()
        return session
    }

    fun remove(code: String, hostSecret: String): Boolean {
        val session = sessions[code.uppercase()] ?: return false
        if (session.hostSecret != hostSecret) return false
        return sessions.remove(session.code, session)
    }

    fun knock(code: String, knock: Knock): Boolean {
        val session = find(code) ?: return false
        session.mailbox(knock.toMember).trySend(knock)
        return true
    }

    /** Returns as soon as one knock waits, everything queued behind it included, or empty when [waitMilliseconds] pass. */
    suspend fun awaitKnocks(code: String, member: Int, waitMilliseconds: Long): List<Knock>? {
        val session = find(code) ?: return null
        val mailbox = session.mailbox(member)
        val first = withTimeoutOrNull(waitMilliseconds) { mailbox.receive() } ?: return emptyList()
        val knocks = mutableListOf(first)
        while (true) knocks += mailbox.tryReceive().getOrNull() ?: break
        return knocks
    }

    val size: Int
        get() = sessions.size

    private fun expire() {
        val now = clock()
        sessions.values.removeIf { session -> now - session.lastSeenMilliseconds > timeToLiveMilliseconds }
    }

    private fun randomCode(): String = buildString {
        repeat(RendezvousProtocol.CODE_LENGTH) { append(RendezvousProtocol.CODE_ALPHABET[random.nextInt(RendezvousProtocol.CODE_ALPHABET.length)]) }
    }

    private fun randomSecret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also(random::nextBytes))
}
