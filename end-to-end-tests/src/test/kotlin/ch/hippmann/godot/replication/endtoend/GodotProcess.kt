package ch.hippmann.godot.replication.endtoend

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** One headless Godot running the sample with a scenario; its markers are collected while it runs. */
class GodotProcess(val name: String, arguments: List<String>, logFile: File, val expectsAbruptExit: Boolean = false) {
    private val process: Process
    private val reader: Thread

    val events = CopyOnWriteArrayList<ScenarioEvent>()

    @Volatile
    var result: ScenarioResult? = null
        private set

    val logFile: File = logFile

    init {
        logFile.parentFile.mkdirs()
        val command = buildList {
            if (File("/usr/bin/stdbuf").exists()) addAll(listOf("/usr/bin/stdbuf", "-oL"))
            add(SampleProject.godotExecutable)
            addAll(listOf("--headless", "--path", SampleProject.directory.absolutePath, "--"))
            addAll(arguments)
        }
        process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .apply {
                environment().remove("DISPLAY")
                environment().remove("WAYLAND_DISPLAY")
            }
            .start()
        reader = thread(name = "godot-$name-output", isDaemon = true) {
            logFile.bufferedWriter().use { writer ->
                process.inputStream.bufferedReader().forEachLine { line ->
                    writer.appendLine(line)
                    writer.flush()
                    ScenarioMarkers.parseEvent(line)?.let(events::add)
                    ScenarioMarkers.parseResult(line)?.let { result = it }
                }
            }
        }
    }

    fun awaitEvent(eventName: String, timeoutMilliseconds: Long): ScenarioEvent {
        val deadline = System.currentTimeMillis() + timeoutMilliseconds
        while (System.currentTimeMillis() < deadline) {
            events.firstOrNull { event -> event.name == eventName }?.let { return it }
            check(process.isAlive) { "$name exited before reporting $eventName (exit code ${process.exitValue()}), see $logFile" }
            Thread.sleep(POLL_MILLISECONDS)
        }
        error("$name did not report $eventName within ${timeoutMilliseconds}ms, see $logFile")
    }

    fun awaitExit(timeoutMilliseconds: Long): Int {
        if (!process.waitFor(timeoutMilliseconds, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor(FORCE_KILL_MILLISECONDS, TimeUnit.MILLISECONDS)
            error("$name did not exit within ${timeoutMilliseconds}ms and was killed, see $logFile")
        }
        reader.join(FORCE_KILL_MILLISECONDS)
        return process.exitValue()
    }

    fun kill() {
        process.destroyForcibly()
    }

    fun logTail(lines: Int = 40): String = logFile.takeIf { it.exists() }?.readLines()?.takeLast(lines)?.joinToString("\n") ?: ""

    private companion object {
        const val POLL_MILLISECONDS = 50L
        const val FORCE_KILL_MILLISECONDS = 5_000L
    }
}
