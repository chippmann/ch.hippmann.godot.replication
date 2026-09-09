package ch.hippmann.godot.replication.rendezvous

import ch.hippmann.godot.replication.core.rendezvous.Knock
import ch.hippmann.godot.replication.core.rendezvous.Knocks
import ch.hippmann.godot.replication.core.rendezvous.ObservedEndpoint
import ch.hippmann.godot.replication.core.rendezvous.PublishedSession
import ch.hippmann.godot.replication.core.rendezvous.RelayAllocation
import ch.hippmann.godot.replication.core.rendezvous.RelayRequest
import ch.hippmann.godot.replication.core.rendezvous.ServiceInformation
import ch.hippmann.godot.replication.core.rendezvous.SessionHeartbeat
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistered
import ch.hippmann.godot.replication.core.rendezvous.SessionRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The rendezvous service over plain JDK HTTP; every call leaves the main thread and comes back to it. */
class RendezvousClient(serviceUrl: String, private val timeoutMilliseconds: Long = DEFAULT_TIMEOUT_MILLISECONDS) {
    val baseUrl: String = serviceUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMilliseconds)).build()

    class RendezvousException(message: String) : RuntimeException(message)

    suspend fun information(): ServiceInformation = get("/", ServiceInformation.serializer()) ?: throw RendezvousException("No service at $baseUrl")

    suspend fun register(registration: SessionRegistration): SessionRegistered =
        post("/sessions", SessionRegistration.serializer(), registration, SessionRegistered.serializer()) ?: throw RendezvousException("Registration refused")

    suspend fun heartbeat(code: String, heartbeat: SessionHeartbeat): PublishedSession? =
        put("/sessions/$code", SessionHeartbeat.serializer(), heartbeat, PublishedSession.serializer())

    suspend fun find(code: String): PublishedSession? = get("/sessions/${code.uppercase()}", PublishedSession.serializer())

    suspend fun remove(code: String, secret: String) {
        send(HttpRequest.newBuilder(uri("/sessions/$code")).header(HOST_SECRET_HEADER, secret).DELETE())
    }

    suspend fun knock(code: String, knock: Knock): Boolean =
        send(request("/sessions/$code/knocks").POST(body(Knock.serializer(), knock))).statusCode() in 200..299

    suspend fun awaitKnocks(code: String, member: Int, waitSeconds: Long): List<Knock> =
        get("/sessions/$code/knocks/$member?wait=$waitSeconds", Knocks.serializer(), waitSeconds * 1_000 + timeoutMilliseconds)?.knocks ?: emptyList()

    suspend fun observe(token: Long): ObservedEndpoint? = get("/observe/$token", ObservedEndpoint.serializer())

    suspend fun allocateRelay(code: String, request: RelayRequest): RelayAllocation? =
        post("/sessions/$code/relays", RelayRequest.serializer(), request, RelayAllocation.serializer())

    private suspend fun <T> get(path: String, deserializer: KSerializer<T>, timeout: Long = timeoutMilliseconds): T? =
        parse(send(request(path, timeout).GET()), deserializer)

    private suspend fun <B, T> post(path: String, bodySerializer: KSerializer<B>, body: B, deserializer: KSerializer<T>): T? =
        parse(send(request(path).POST(body(bodySerializer, body))), deserializer)

    private suspend fun <B, T> put(path: String, bodySerializer: KSerializer<B>, body: B, deserializer: KSerializer<T>): T? =
        parse(send(request(path).PUT(body(bodySerializer, body))), deserializer)

    private fun <T> parse(response: HttpResponse<String>, deserializer: KSerializer<T>): T? =
        if (response.statusCode() in 200..299) json.decodeFromString(deserializer, response.body()) else null

    private fun <B> body(serializer: KSerializer<B>, body: B): HttpRequest.BodyPublisher =
        HttpRequest.BodyPublishers.ofString(json.encodeToString(serializer, body))

    private fun request(path: String, timeout: Long = timeoutMilliseconds): HttpRequest.Builder =
        HttpRequest.newBuilder(uri(path)).timeout(Duration.ofMillis(timeout)).header("Content-Type", "application/json")

    private fun uri(path: String): URI = URI.create(baseUrl + path)

    private suspend fun send(request: HttpRequest.Builder): HttpResponse<String> = withContext(Dispatchers.IO) {
        http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLISECONDS = 5_000L
        const val HOST_SECRET_HEADER = "X-Host-Secret"
    }
}
