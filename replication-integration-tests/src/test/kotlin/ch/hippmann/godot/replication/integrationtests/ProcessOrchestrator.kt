package ch.hippmann.godot.replication.integrationtests

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class PeerHandle internal constructor(
    val peerName: String,
    val role: String,
    val process: Process,
    val stdoutFile: File,
    val stderrFile: File,
)

data class PeerResult(
    val peerName: String,
    val role: String,
    val passed: Boolean,
    val data: JsonObject,
    val error: String?,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class ProcessOrchestrator(
    private val godotBinary: String,
    private val godotProjectDir: File,
) {
    private val handles = mutableListOf<PeerHandle>()
    val port: Int = ServerSocket(0).use { it.localPort }
    val resultDir: File = Files.createTempDirectory("replication-it-").toFile()

    /** Launch a peer running [scenarioClass]. The scenario's [TestScene] annotation (or the default) becomes the Godot main scene. */
    fun launch(
        scenarioClass: KClass<out TestScenario>,
        role: Role,
        peerName: String,
        expectedClientCount: Int = 0,
    ): PeerHandle {
        // Read the scene path via the @TestScene annotation rather than by
        // instantiating the scenario class — instantiation would trigger field
        // initializers that may reference godot.core.* types not on the
        // orchestrator's classpath (see KDoc on [TestScene]).
        val scenePath = TestScenario.scenePathFor(scenarioClass.java)
        val scenarioFullyQualifiedClassName = scenarioClass.java.name

        val stdoutFile = File(resultDir, "$peerName.out")
        val stderrFile = File(resultDir, "$peerName.err")

        val command = buildList {
            add(godotBinary)
            add("--headless")
            add("--path"); add(godotProjectDir.absolutePath)
            add(scenePath)
            add("--")
            add("--scenario"); add(scenarioFullyQualifiedClassName)
            add("--role"); add(role.name)
            add("--port"); add(port.toString())
            add("--peer-name"); add(peerName)
            add("--result-dir"); add(resultDir.absolutePath)
            if (expectedClientCount > 0) {
                add("--expected-client-count"); add(expectedClientCount.toString())
            }
        }

        println("[orchestrator] launching $peerName (${role.name}): ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectOutput(stdoutFile)
            .redirectError(stderrFile)
            .directory(godotProjectDir)
            .start()

        val handle = PeerHandle(peerName, role.name, process, stdoutFile, stderrFile)
        handles += handle
        return handle
    }

    /**
     * Force-kill a specific peer to simulate crash / network loss. Tries a graceful
     * shutdown first ([gracefulShutdownTimeoutMs]), then `destroyForcibly`.
     */
    fun killPeer(handle: PeerHandle, gracefulShutdownTimeoutMs: Long = 2_000) {
        if (!handle.process.isAlive) return
        handle.process.destroy()
        if (!handle.process.waitFor(gracefulShutdownTimeoutMs, TimeUnit.MILLISECONDS)) {
            handle.process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
        }
    }

    /** Wait for all launched peers to exit, force-killing any that overrun [timeoutSeconds]. */
    fun awaitAll(timeoutSeconds: Long): List<PeerResult> {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        for (handle in handles) {
            val remainingNanos = (deadlineNanos - System.nanoTime()).coerceAtLeast(0)
            val finishedInTime = handle.process.waitFor(remainingNanos, TimeUnit.NANOSECONDS)
            if (!finishedInTime) {
                System.err.println("[orchestrator] ${handle.peerName} timed out, force-killing")
                handle.process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
            }
        }
        return handles.map { collectResult(it) }
    }

    fun cleanup() {
        for (handle in handles) {
            if (handle.process.isAlive) handle.process.destroyForcibly()
        }
    }

    private fun collectResult(handle: PeerHandle): PeerResult {
        val resultFile = File(resultDir, "${handle.peerName}.json")
        val stdout = handle.stdoutFile.takeIf { it.exists() }?.readText().orEmpty()
        val stderr = handle.stderrFile.takeIf { it.exists() }?.readText().orEmpty()

        if (!resultFile.exists()) {
            return PeerResult(
                peerName = handle.peerName,
                role = handle.role,
                passed = false,
                data = JsonObject(emptyMap()),
                error = "no result file written (process likely crashed before scenario completed)",
                exitCode = handle.process.exitValue(),
                stdout = stdout,
                stderr = stderr,
            )
        }
        val raw = Json.parseToJsonElement(resultFile.readText()).jsonObject
        return PeerResult(
            peerName = raw["peerName"]!!.jsonPrimitive.content,
            role = raw["role"]!!.jsonPrimitive.content,
            passed = raw["passed"]!!.jsonPrimitive.content.toBoolean(),
            data = raw["data"]?.jsonObject ?: JsonObject(emptyMap()),
            error = raw["error"]?.jsonPrimitive?.content,
            exitCode = handle.process.exitValue(),
            stdout = stdout,
            stderr = stderr,
        )
    }
}

fun renderMultiPeerFailure(results: List<PeerResult>): String = buildString {
    appendLine("=== Multi-peer test failure ===")
    for (result in results) {
        appendLine("--- ${result.peerName} (${result.role}) passed=${result.passed} exit=${result.exitCode} ---")
        if (result.error != null) appendLine("ERROR: ${result.error}")
        appendLine("DATA: ${result.data}")
        if (result.stdout.isNotBlank()) appendLine("STDOUT:\n${result.stdout.takeLast(4000)}")
        if (result.stderr.isNotBlank()) appendLine("STDERR:\n${result.stderr.takeLast(4000)}")
    }
}
