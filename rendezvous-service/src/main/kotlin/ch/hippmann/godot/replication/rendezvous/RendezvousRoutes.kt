package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.rendezvous.Knocks
import ch.hippmann.godot.replication.core.rendezvous.RelayAllocation
import ch.hippmann.godot.replication.core.rendezvous.RelayRequest
import ch.hippmann.godot.replication.core.rendezvous.RendezvousProtocol
import ch.hippmann.godot.replication.core.rendezvous.ServiceInformation
import ch.hippmann.godot.replication.core.rendezvous.SessionHeartbeat
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistered
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistration
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

class RendezvousService(
    val configuration: ServiceConfiguration,
    val registry: SessionRegistry,
    val udp: UdpEndpoint,
    val relay: Relay,
)

/** The address a client should send UDP to: configured explicitly, else the host it reached us on. */
private fun RoutingContext.publicAddress(service: RendezvousService): String =
    service.configuration.publicAddress.ifBlank { call.request.local.serverHost }

fun Application.rendezvousModule(service: RendezvousService) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
    routing {
        get("/") {
            call.respond(ServiceInformation(RendezvousProtocol.VERSION, publicAddress(service), service.udp.port))
        }
        post("/sessions") {
            val registration = call.receive<SessionRegistration>()
            if (registration.protocolVersion != RendezvousProtocol.VERSION) {
                call.respond(HttpStatusCode.BadRequest, "Unsupported protocol version ${registration.protocolVersion}")
                return@post
            }
            val session = service.registry.register(registration)
            call.respond(HttpStatusCode.Created, SessionRegistered(session.code, session.hostSecret))
        }
        get("/sessions/{code}") {
            val session = service.registry.find(call.parameters["code"].orEmpty())
            if (session == null) call.respond(HttpStatusCode.NotFound) else call.respond(session.published())
        }
        put("/sessions/{code}") {
            val session = service.registry.heartbeat(call.parameters["code"].orEmpty(), call.receive<SessionHeartbeat>())
            if (session == null) call.respond(HttpStatusCode.NotFound) else call.respond(session.published())
        }
        delete("/sessions/{code}") {
            val secret = call.request.headers[HOST_SECRET_HEADER].orEmpty()
            val removed = service.registry.remove(call.parameters["code"].orEmpty(), secret)
            call.respond(if (removed) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
        post("/sessions/{code}/knocks") {
            val accepted = service.registry.knock(call.parameters["code"].orEmpty(), call.receive<Knock>())
            call.respond(if (accepted) HttpStatusCode.Accepted else HttpStatusCode.NotFound)
        }
        get("/sessions/{code}/knocks/{member}") {
            val member = call.parameters["member"]?.toIntOrNull() ?: RendezvousProtocol.JOINER
            val wait = (call.request.queryParameters["wait"]?.toLongOrNull() ?: 0L).coerceIn(0L, MAXIMUM_WAIT_SECONDS) * 1_000
            val knocks = service.registry.awaitKnocks(call.parameters["code"].orEmpty(), member, wait)
            if (knocks == null) call.respond(HttpStatusCode.NotFound) else call.respond(Knocks(knocks))
        }
        get("/observe/{token}") {
            val token = call.parameters["token"]?.toLongOrNull()
            val observed = token?.let(service.udp::observed)
            if (observed == null) call.respond(HttpStatusCode.NotFound) else call.respond(observed)
        }
        post("/sessions/{code}/relays") {
            call.receive<RelayRequest>()
            if (service.registry.find(call.parameters["code"].orEmpty()) == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val pair = service.relay.allocate()
            if (pair == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "No relay ports left")
                return@post
            }
            call.respond(HttpStatusCode.Created, RelayAllocation(publicAddress(service), pair.callerPort, pair.calleePort))
        }
    }
}

const val HOST_SECRET_HEADER = "X-Host-Secret"
private const val MAXIMUM_WAIT_SECONDS = 25L
