package ch.hippmann.godot.replication.endtoend

import java.io.File
import java.net.DatagramSocket
import kotlin.test.assertEquals
import kotlin.test.fail

/** Starts one host and any number of clients of a scenario on localhost and judges their markers. */
class GodotCluster(private val testName: String, private val scenario: String) : AutoCloseable {
    private val processes = mutableListOf<GodotProcess>()
    private val logsDirectory = File(SampleProject.logsDirectory, testName)

    val hostPort: Int = freeUdpPort()

    fun startHost(name: String, vararg extra: String, timeoutSeconds: Long = SCENARIO_TIMEOUT_SECONDS): GodotProcess {
        val host = start(name, "--role=host", "--port=$hostPort", "--timeout-seconds=$timeoutSeconds", *extra)
        host.awaitEvent("hosting", STARTUP_TIMEOUT_MILLISECONDS)
        return host
    }

    fun startClient(name: String, vararg extra: String, timeoutSeconds: Long = SCENARIO_TIMEOUT_SECONDS, abruptExit: Boolean = false): GodotProcess =
        start(name, "--role=client", "--join=127.0.0.1:$hostPort", "--port=${freeUdpPort()}", "--timeout-seconds=$timeoutSeconds", *extra, abruptExit = abruptExit)

    fun awaitAllPassed(timeoutMilliseconds: Long = EXIT_TIMEOUT_MILLISECONDS) {
        val failures = mutableListOf<String>()
        for (process in processes) {
            val exitCode = runCatching { process.awaitExit(timeoutMilliseconds) }.getOrElse { failure ->
                failures += failure.message.orEmpty()
                continue
            }
            val result = process.result
            if ((exitCode != 0 && !process.expectsAbruptExit) || result !is ScenarioResult.Pass) {
                failures += "${process.name}: exit $exitCode, result $result\n${process.logTail()}"
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n\n"))
    }

    fun eventsOf(process: GodotProcess, name: String): List<ScenarioEvent> = process.events.filter { event -> event.name == name }

    fun assertEvent(process: GodotProcess, name: String): ScenarioEvent =
        process.events.firstOrNull { event -> event.name == name } ?: fail("${process.name} never reported $name\n${process.logTail()}")

    fun assertEventCount(process: GodotProcess, name: String, expected: Int) {
        assertEquals(expected, eventsOf(process, name).size, "${process.name} reported $name")
    }

    override fun close() {
        processes.forEach(GodotProcess::kill)
    }

    private fun start(name: String, vararg arguments: String, abruptExit: Boolean = false): GodotProcess {
        val verbose = if (System.getProperty("verbose.transport") == "true") listOf("--verbose-transport=true") else emptyList()
        val process = GodotProcess(name, listOf("--scenario=$scenario", "--name=$name", *arguments) + verbose, File(logsDirectory, "$name.log"), abruptExit)
        processes += process
        return process
    }

    companion object {
        const val SCENARIO_TIMEOUT_SECONDS = 90L
        const val STARTUP_TIMEOUT_MILLISECONDS = 60_000L
        const val EXIT_TIMEOUT_MILLISECONDS = 120_000L

        fun freeUdpPort(): Int = DatagramSocket(0).use { socket -> socket.localPort }
    }
}
