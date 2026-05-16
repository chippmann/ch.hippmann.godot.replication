package ch.hippmann.godot.replication.it

import godot.api.ENetMultiplayerPeer
import godot.api.Node
import godot.api.SceneTree
import godot.core.connect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import java.io.File

class TestContext(
    val runner: Node,
    val args: TestArgs,
) {
    val tree: SceneTree get() = runner.getTree()!!
    val multiplayer get() = runner.multiplayer!!

    private val collected: MutableMap<String, kotlinx.serialization.json.JsonElement> = mutableMapOf()

    fun startServer() {
        val peer = ENetMultiplayerPeer()
        val err = peer.createServer(args.port, maxClients = args.clientCount.coerceAtLeast(1))
        check(err.value == 0L) { "createServer failed: $err" }
        multiplayer.multiplayerPeer = peer
    }

    fun connectToServer(host: String = "127.0.0.1") {
        val peer = ENetMultiplayerPeer()
        val err = peer.createClient(host, args.port)
        check(err.value == 0L) { "createClient failed: $err" }
        multiplayer.multiplayerPeer = peer
    }

    suspend fun awaitClientsConnected(count: Int, timeoutMs: Long = 10_000) {
        if (count == 0) return
        val deferred = CompletableDeferred<Unit>()
        var seen = 0
        val handler: (Long) -> Unit = {
            seen += 1
            if (seen >= count && !deferred.isCompleted) deferred.complete(Unit)
        }
        multiplayer.peerConnected.connect(handler)
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (t: TimeoutCancellationException) {
            throw IllegalStateException("only $seen/$count clients connected before timeout")
        }
    }

    suspend fun awaitServerConnected(timeoutMs: Long = 10_000) {
        val deferred = CompletableDeferred<Unit>()
        multiplayer.connectedToServer.connect {
            if (!deferred.isCompleted) deferred.complete(Unit)
        }
        multiplayer.connectionFailed.connect {
            if (!deferred.isCompleted) deferred.completeExceptionally(IllegalStateException("connection_failed"))
        }
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

    fun put(key: String, value: kotlinx.serialization.json.JsonElement) {
        collected[key] = value
    }

    fun put(key: String, value: String) = put(key, kotlinx.serialization.json.JsonPrimitive(value))
    fun put(key: String, value: Int) = put(key, kotlinx.serialization.json.JsonPrimitive(value))
    fun put(key: String, value: Long) = put(key, kotlinx.serialization.json.JsonPrimitive(value))
    fun put(key: String, value: Boolean) = put(key, kotlinx.serialization.json.JsonPrimitive(value))

    fun report(block: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        collected.forEach { (k, v) -> put(k, v) }
        block()
    }

    fun writeResult(passed: Boolean, data: JsonObject, error: String? = null) {
        val payload = buildJsonObject {
            put("peerId", kotlinx.serialization.json.JsonPrimitive(args.peerId))
            put("role", kotlinx.serialization.json.JsonPrimitive(args.role.name))
            put("scenario", kotlinx.serialization.json.JsonPrimitive(args.scenarioFqcn))
            put("passed", kotlinx.serialization.json.JsonPrimitive(passed))
            put("data", data)
            if (error != null) put("error", kotlinx.serialization.json.JsonPrimitive(error))
        }
        val dir = File(args.resultDir).also { it.mkdirs() }
        File(dir, "${args.peerId}.json").writeText(payload.toString())
    }
}
