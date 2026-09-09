package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.rendezvous.KnockAnswer
import ch.hippmann.godot.replication.core.rendezvous.Knocks
import ch.hippmann.godot.replication.core.rendezvous.PublishedSession
import ch.hippmann.godot.replication.core.rendezvous.RelayAllocation
import ch.hippmann.godot.replication.core.rendezvous.RelayRequest
import ch.hippmann.godot.replication.core.rendezvous.RendezvousProtocol
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistered
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistration
import ch.hippmann.godot.replication.core.session.Endpoint
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class RendezvousRoutesTest {
    private val registration = SessionRegistration(
        lobbyName = "Tobias' hangar", maximumPlayers = 4, passwordRequired = true, encrypted = true, masterId = 2,
        masterCertificate = "cert", masterEndpoints = listOf(Endpoint("203.0.113.7", 7777)),
    )

    @Test
    fun `a session is registered, published, knocked on, relayed and removed`() = testApplication {
        val udp = UdpEndpoint("127.0.0.1", 0)
        val relay = Relay("127.0.0.1", 47820..47830, idleMilliseconds = 60_000)
        val configuration = ServiceConfiguration(publicAddress = "203.0.113.1")
        application { rendezvousModule(RendezvousService(configuration, SessionRegistry(45_000), udp, relay)) }
        val client = createClient { install(ContentNegotiation) { json() } }
        try {
            val registered = client.post("/sessions") { contentType(ContentType.Application.Json); setBody(registration) }.body<SessionRegistered>()
            assertEquals(RendezvousProtocol.CODE_LENGTH, registered.code.length)

            val published = client.get("/sessions/${registered.code}").body<PublishedSession>()
            assertEquals("Tobias' hangar", published.lobbyName)
            assertEquals(listOf(Endpoint("203.0.113.7", 7777)), published.masterEndpoints)

            val knock = Knock(RendezvousProtocol.JOINER, 2, listOf(Endpoint("198.51.100.9", 40000)), token = 7)
            assertEquals(HttpStatusCode.Accepted, client.post("/sessions/${registered.code}/knocks") { contentType(ContentType.Application.Json); setBody(knock) }.status)
            assertEquals(listOf(knock), client.get("/sessions/${registered.code}/knocks/2?wait=1").body<Knocks>().knocks)
            assertEquals(emptyList(), client.get("/sessions/${registered.code}/knocks/2?wait=0").body<Knocks>().knocks)

            assertEquals(HttpStatusCode.NotFound, client.get("/sessions/${registered.code}/knocks/7/answer?wait=0").status)
            val answer = KnockAnswer(7, listOf(Endpoint("203.0.113.7", 50123)))
            assertEquals(HttpStatusCode.Accepted, client.post("/sessions/${registered.code}/knocks/7/answer") { contentType(ContentType.Application.Json); setBody(answer) }.status)
            assertEquals(answer, client.get("/sessions/${registered.code}/knocks/7/answer?wait=1").body<KnockAnswer>())

            val allocation = client.post("/sessions/${registered.code}/relays") { contentType(ContentType.Application.Json); setBody(RelayRequest(2, 3)) }.body<RelayAllocation>()
            assertEquals("203.0.113.1", allocation.address)
            assertEquals(1, relay.activePairs)

            assertEquals(HttpStatusCode.NotFound, client.get("/observe/123").status)
            assertEquals(HttpStatusCode.NotFound, client.delete("/sessions/${registered.code}") { header(HOST_SECRET_HEADER, "wrong") }.status)
            assertEquals(HttpStatusCode.NoContent, client.delete("/sessions/${registered.code}") { header(HOST_SECRET_HEADER, registered.hostSecret) }.status)
            assertEquals(HttpStatusCode.NotFound, client.get("/sessions/${registered.code}").status)
        } finally {
            relay.close()
            udp.close()
        }
    }
}
