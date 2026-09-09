package ch.hippmann.godot.replication.endtoend

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.fail

/** The rendezvous service as its own JVM process on free localhost ports, the way it runs in production. */
class RendezvousServer(logsDirectory: File) : AutoCloseable {
    val httpPort: Int = GodotCluster.freeUdpPort()
    val udpPort: Int = GodotCluster.freeUdpPort()
    private val relayFirstPort: Int = 30_000 + (System.nanoTime() % 20_000).toInt()
    val url: String = "http://127.0.0.1:$httpPort"
    private val logFile = File(logsDirectory, "rendezvous.log").also { it.parentFile.mkdirs() }
    private val process: Process

    init {
        val launcher = System.getProperty("rendezvous.launcher") ?: fail("rendezvous.launcher is not set")
        process = ProcessBuilder(launcher)
            .redirectErrorStream(true)
            .redirectOutput(logFile)
            .apply {
                environment()["RENDEZVOUS_BIND_ADDRESS"] = "127.0.0.1"
                environment()["RENDEZVOUS_HTTP_PORT"] = httpPort.toString()
                environment()["RENDEZVOUS_UDP_PORT"] = udpPort.toString()
                environment()["RENDEZVOUS_RELAY_PORT_RANGE"] = "$relayFirstPort-${relayFirstPort + 40}"
                environment()["RENDEZVOUS_PUBLIC_ADDRESS"] = "127.0.0.1"
                environment()["JAVA_HOME"] = System.getProperty("java.home")
            }
            .start()
        awaitReady()
    }

    private fun awaitReady() {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MILLISECONDS
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) fail("The rendezvous service exited early:\n${logFile.readText().takeLast(2_000)}")
            val connection = runCatching { URI.create("$url/").toURL().openConnection() as HttpURLConnection }.getOrNull()
            if (connection != null && runCatching { connection.responseCode }.getOrNull() == 200) return
            Thread.sleep(200)
        }
        fail("The rendezvous service did not answer within $STARTUP_TIMEOUT_MILLISECONDS ms:\n${logFile.readText().takeLast(2_000)}")
    }

    override fun close() {
        process.destroy()
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
    }

    private companion object {
        const val STARTUP_TIMEOUT_MILLISECONDS = 30_000L
    }
}
