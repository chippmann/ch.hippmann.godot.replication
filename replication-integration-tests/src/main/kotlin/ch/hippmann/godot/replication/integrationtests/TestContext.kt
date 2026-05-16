package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestReplicator
import godot.api.ENetMultiplayerPeer
import godot.api.MultiplayerAPI
import godot.api.Node
import godot.api.SceneTree
import godot.core.Error
import godot.core.asCallable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

class TestContext(
    val runner: Node,
    val args: TestArgs,
) {
    val tree: SceneTree get() = runner.getTree()!!
    val multiplayer: MultiplayerAPI get() = runner.multiplayer!!

    /** The Replicator node configured by the scene at relative path `Replicator`. */
    val replicator: IntegrationTestReplicator
        get() = runner.getNodeOrNull("Replicator") as? IntegrationTestReplicator
            ?: error("scene does not contain a 'Replicator' child of TestRunner — wrong scene loaded?")

    private val collectedResultFields: MutableMap<String, JsonElement> = mutableMapOf()

    // ----- Multiplayer setup -----

    fun startServer() {
        val peer = ENetMultiplayerPeer()
        val error = peer.createServer(
            port = args.port,
            maxClients = args.expectedClientCount.coerceAtLeast(1),
        )
        check(error == Error.OK) { "createServer failed: $error" }
        multiplayer.multiplayerPeer = peer
    }

    fun connectToServer(host: String = "127.0.0.1") {
        val peer = ENetMultiplayerPeer()
        val error = peer.createClient(address = host, port = args.port)
        check(error == Error.OK) { "createClient failed: $error" }
        multiplayer.multiplayerPeer = peer
    }

    // ----- Awaits -----

    suspend fun awaitClientsConnected(count: Int, timeoutMs: Long = 10_000) {
        if (count == 0) return
        val allConnected = CompletableDeferred<Unit>()
        var seenCount = 0
        val onPeerConnected: (Long) -> Unit = { _ ->
            seenCount += 1
            if (seenCount >= count && !allConnected.isCompleted) allConnected.complete(Unit)
        }
        multiplayer.peerConnected.connect(onPeerConnected.asCallable {})
        try {
            withTimeout(timeoutMs) { allConnected.await() }
        } catch (cancellation: TimeoutCancellationException) {
            throw IllegalStateException("only $seenCount/$count clients connected before $timeoutMs ms")
        }
    }

    suspend fun awaitServerConnected(timeoutMs: Long = 10_000) {
        val deferred = CompletableDeferred<Unit>()
        val onConnected: () -> Unit = {
            if (!deferred.isCompleted) deferred.complete(Unit)
        }
        val onConnectionFailed: () -> Unit = {
            if (!deferred.isCompleted) deferred.completeExceptionally(IllegalStateException("connection_failed"))
        }
        multiplayer.connectedToServer.connect(onConnected.asCallable {})
        multiplayer.connectionFailed.connect(onConnectionFailed.asCallable {})
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (cancellation: TimeoutCancellationException) {
            throw IllegalStateException("did not reach connected_to_server within $timeoutMs ms")
        }
    }

    suspend fun awaitServerDisconnected(timeoutMs: Long = 10_000) {
        val deferred = CompletableDeferred<Unit>()
        val onServerDisconnected: () -> Unit = {
            if (!deferred.isCompleted) deferred.complete(Unit)
        }
        multiplayer.serverDisconnected.connect(onServerDisconnected.asCallable {})
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (cancellation: TimeoutCancellationException) {
            throw IllegalStateException("server did not disconnect within $timeoutMs ms")
        }
    }

    suspend fun awaitAllClientsDisconnected(timeoutMs: Long = 30_000) {
        pollUntil(timeoutMs) { multiplayer.getPeers().size == 0 }
    }

    suspend fun pollUntil(timeoutMs: Long = 5_000, intervalMs: Long = 50, predicate: () -> Boolean) {
        try {
            withTimeout(timeoutMs) {
                while (!predicate()) delay(intervalMs)
            }
        } catch (cancellation: TimeoutCancellationException) {
            throw IllegalStateException("pollUntil timed out after $timeoutMs ms")
        }
    }

    // ----- Result reporting -----

    fun put(key: String, value: JsonElement) {
        collectedResultFields[key] = value
    }

    fun put(key: String, value: String) = put(key, JsonPrimitive(value))
    fun put(key: String, value: Int) = put(key, JsonPrimitive(value))
    fun put(key: String, value: Long) = put(key, JsonPrimitive(value))
    fun put(key: String, value: Boolean) = put(key, JsonPrimitive(value))

    fun reportSnapshot(): JsonObject = buildJsonObject {
        collectedResultFields.forEach { (key, value) -> put(key, value) }
    }

    fun writeResult(passed: Boolean, error: String? = null) {
        val payload = buildJsonObject {
            put("peerName", JsonPrimitive(args.peerName))
            put("role", JsonPrimitive(args.role.name))
            put("scenario", JsonPrimitive(args.scenarioFullyQualifiedClassName))
            put("passed", JsonPrimitive(passed))
            put("data", reportSnapshot())
            if (error != null) put("error", JsonPrimitive(error))
        }
        val directory = File(args.resultDir).also { it.mkdirs() }
        File(directory, "${args.peerName}.json").writeText(payload.toString())
    }
}
