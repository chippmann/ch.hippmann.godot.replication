package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.rendezvous.RendezvousProtocol
import ch.hippmann.godot.replication.core.rendezvous.SessionHeartbeat
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistration
import ch.hippmann.godot.replication.core.session.Endpoint
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRegistryTest {
    private var now = 1_000_000L
    private val registry = SessionRegistry(timeToLiveMilliseconds = 45_000) { now }
    private val registration = SessionRegistration(
        lobbyName = "Mara's arena", maximumPlayers = 8, passwordRequired = false, encrypted = true, masterId = 2,
        masterCertificate = "-----BEGIN CERTIFICATE-----", masterEndpoints = listOf(Endpoint("203.0.113.7", 7777)),
    )

    @Test
    fun `codes use the readable alphabet and sessions expire without heartbeats`() {
        val session = registry.register(registration)
        assertEquals(RendezvousProtocol.CODE_LENGTH, session.code.length)
        assertTrue(session.code.all { character -> character in RendezvousProtocol.CODE_ALPHABET })
        assertNotNull(registry.find(session.code.lowercase()))
        now += 44_000
        assertNotNull(registry.heartbeat(session.code, SessionHeartbeat(session.hostSecret, 3, 2, registration.masterEndpoints, registration.masterCertificate)))
        assertEquals(3, registry.find(session.code)!!.published().playerCount)
        now += 46_000
        assertNull(registry.find(session.code))
    }

    @Test
    fun `only the host secret updates or removes a session`() {
        val session = registry.register(registration)
        assertNull(registry.heartbeat(session.code, SessionHeartbeat("wrong", 2, 2, emptyList(), "")))
        assertFalse(registry.remove(session.code, "wrong"))
        assertTrue(registry.remove(session.code, session.hostSecret))
        assertNull(registry.find(session.code))
    }

    @Test
    fun `knocks wait in the mailbox of the member they address`() = runBlocking {
        val session = registry.register(registration)
        assertEquals(emptyList(), registry.awaitKnocks(session.code, 2, waitMilliseconds = 10))
        assertTrue(registry.knock(session.code, Knock(RendezvousProtocol.JOINER, 2, listOf(Endpoint("198.51.100.9", 40000)), token = 42)))
        assertTrue(registry.knock(session.code, Knock(3, 2, listOf(Endpoint("198.51.100.10", 40001)), token = 43)))
        val knocks = registry.awaitKnocks(session.code, 2, waitMilliseconds = 1_000)!!
        assertEquals(listOf(42L, 43L), knocks.map { knock -> knock.token })
        assertEquals(emptyList(), registry.awaitKnocks(session.code, 3, waitMilliseconds = 10))
        assertNull(registry.awaitKnocks("NOSUCH", 2, waitMilliseconds = 10))
    }
}
