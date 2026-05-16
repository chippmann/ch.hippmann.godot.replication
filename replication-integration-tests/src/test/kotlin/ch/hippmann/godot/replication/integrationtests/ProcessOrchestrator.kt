package ch.hippmann.godot.replication.it

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class GodotPeerProcess(
    val peerId: String,
    val role: String,
    val process: Process,
    val stdoutFile: File,
    val stderrFile: File,
)

data class PeerResult(
    val peerId: String,
    val role: String,
    val passed: Boolean,
    val data: JsonObject,
    val error: String?,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

class ProcessOrchestrator(
    private val godotBin: String,
    private val projectDir: File,
) {
    private val processes = mutableListOf<GodotPeerProcess>()
    private val port: Int = ServerSocket(0).use { it.localPort }
    private val resultDir: File = Files.createTempDirectory("repl-it-").toFile()

    fun port(): Int = port
    fun resultDir(): File = resultDir

    fun launch(scenario: String, role: String, peerId: String, clientCount: Int = 0): GodotPeerProcess {
        val stdout = File(resultDir, "$peerId.out")
        val stderr = File(resultDir, "$peerId.err")

        val cmd = mutableListOf(
            godotBin,
            "--headless",
            "--path", projectDir.absolutePath,
            "res://test_runner.tscn",
            "--",
            "--scenario", scenario,
            "--role", role,
            "--port", port.toString(),
            "--peer-id", peerId,
            "--result-dir", resultDir.absolutePath,
        )
        if (clientCount > 0) {
            cmd += listOf("--client-count", clientCount.toString())
        }

        println("[orchestrator] launching $peerId: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd)
            .redirectOutput(stdout)
            .redirectError(stderr)
            .directory(projectDir)
        val process = pb.start()
        val handle = GodotPeerProcess(peerId, role, process, stdout, stderr)
        processes += handle
        return handle
    }

    fun awaitAll(timeoutSeconds: Long): List<PeerResult> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        for (handle in processes) {
            val remainingNs = (deadline - System.nanoTime()).coerceAtLeast(0)
            val finished = handle.process.waitFor(remainingNs, TimeUnit.NANOSECONDS)
            if (!finished) {
                System.err.println("[orchestrator] ${handle.peerId} timed out, force-killing")
                handle.process.destroyForcibly()
                handle.process.waitFor(5, TimeUnit.SECONDS)
            }
        }
        return processes.map { collect(it) }
    }

    fun cleanup() {
        for (handle in processes) {
            if (handle.process.isAlive) handle.process.destroyForcibly()
        }
    }

    private fun collect(handle: GodotPeerProcess): PeerResult {
        val resultFile = File(resultDir, "${handle.peerId}.json")
        val stdout = handle.stdoutFile.takeIf { it.exists() }?.readText().orEmpty()
        val stderr = handle.stderrFile.takeIf { it.exists() }?.readText().orEmpty()

        if (!resultFile.exists()) {
            return PeerResult(
                peerId = handle.peerId,
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
            peerId = raw["peerId"]!!.jsonPrimitive.content,
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

fun renderFailure(results: List<PeerResult>): String = buildString {
    appendLine("=== Multi-peer test failure ===")
    for (r in results) {
        appendLine("--- ${r.peerId} (${r.role}) passed=${r.passed} exit=${r.exitCode} ---")
        if (r.error != null) appendLine("ERROR: ${r.error}")
        appendLine("DATA: ${r.data}")
        if (r.stdout.isNotBlank()) appendLine("STDOUT:\n${r.stdout.takeLast(4000)}")
        if (r.stderr.isNotBlank()) appendLine("STDERR:\n${r.stderr.takeLast(4000)}")
    }
}
