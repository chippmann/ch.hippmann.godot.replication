package ch.hippmann.godot.replication.it

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
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

class TestContext(
    val runner: Node,
    val args: TestArgs,
) {
    val tree: SceneTree get() = runner.getTree()!!
    val multiplayer: MultiplayerAPI get() = runner.multiplayer!!

    private val collected: MutableMap<String, JsonElement> = mutableMapOf()

    fun startServer() {
        val peer = ENetMultiplayerPeer()
        val err = peer.createServer(port = args.port, maxClients = args.clientCount.coerceAtLeast(1))
        check(err == Error.OK) { "createServer failed: $err" }
        multiplayer.multiplayerPeer = peer
    }

    fun connectToServer(host: String = "127.0.0.1") {
        val peer = ENetMultiplayerPeer()
        val err = peer.createClient(address = host, port = args.port)
        check(err == Error.OK) { "createClient failed: $err" }
        multiplayer.multiplayerPeer = peer
    }

    suspend fun awaitClientsConnected(count: Int, timeoutMs: Long = 10_000) {
        if (count == 0) return
        val deferred = CompletableDeferred<Unit>()
        var seen = 0
        val handler: (Long) -> Unit = { _ ->
            seen += 1
            if (seen >= count && !deferred.isCompleted) deferred.complete(Unit)
        }
        multiplayer.peerConnected.connect(handler.asCallable {})
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (t: TimeoutCancellationException) {
            throw IllegalStateException("only $seen/$count clients connected before timeout")
        }
    }

    suspend fun awaitServerConnected(timeoutMs: Long = 10_000) {
        val deferred = CompletableDeferred<Unit>()
        val onConnected: () -> Unit = {
            if (!deferred.isCompleted) deferred.complete(Unit)
        }
        val onFailed: () -> Unit = {
            if (!deferred.isCompleted) deferred.completeExceptionally(IllegalStateException("connection_failed"))
        }
        multiplayer.connectedToServer.connect(onConnected.asCallable {})
        multiplayer.connectionFailed.connect(onFailed.asCallable {})
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (t: TimeoutCancellationException) {
            throw IllegalStateException("did not reach connected_to_server in $timeoutMs ms")
        }
    }

    suspend fun pollUntil(timeoutMs: Long = 5_000, intervalMs: Long = 50, predicate: () -> Boolean) {
        try {
            withTimeout(timeoutMs) {
                while (!predicate()) delay(intervalMs)
            }
        } catch (t: TimeoutCancellationException) {
            throw IllegalStateException("pollUntil timed out after $timeoutMs ms")
        }
    }

    fun put(key: String, value: JsonElement) {
        collected[key] = value
    }

    fun put(key: String, value: String) = put(key, JsonPrimitive(value))
    fun put(key: String, value: Int) = put(key, JsonPrimitive(value))
    fun put(key: String, value: Long) = put(key, JsonPrimitive(value))
    fun put(key: String, value: Boolean) = put(key, JsonPrimitive(value))

    fun report(block: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        collected.forEach { (k, v) -> put(k, v) }
        block()
    }

    fun writeResult(passed: Boolean, data: JsonObject, error: String? = null) {
        val payload = buildJsonObject {
            put("peerId", JsonPrimitive(args.peerId))
            put("role", JsonPrimitive(args.role.name))
            put("scenario", JsonPrimitive(args.scenarioFqcn))
            put("passed", JsonPrimitive(passed))
            put("data", data)
            if (error != null) put("error", JsonPrimitive(error))
        }
        val dir = File(args.resultDir).also { it.mkdirs() }
        File(dir, "${args.peerId}.json").writeText(payload.toString())
    }
}
