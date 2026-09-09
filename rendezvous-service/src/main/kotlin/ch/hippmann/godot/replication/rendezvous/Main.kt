package ch.hippmann.godot.replication.rendezvous

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory

fun main() {
    val configuration = ServiceConfiguration.fromEnvironment()
    val logger = LoggerFactory.getLogger("rendezvous")
    val udp = UdpEndpoint(configuration.bindAddress, configuration.udpPort)
    val relay = Relay(configuration.bindAddress, configuration.relayPorts, configuration.relayIdleSeconds * 1_000)
    val registry = SessionRegistry(configuration.sessionTimeToLiveSeconds * 1_000)
    val service = RendezvousService(configuration, registry, udp, relay)
    logger.info(
        "Rendezvous listening on http {}:{}, udp {}, relay ports {}-{}",
        configuration.bindAddress, configuration.httpPort, udp.port, configuration.relayPorts.first, configuration.relayPorts.last,
    )
    Runtime.getRuntime().addShutdownHook(Thread { relay.close(); udp.close() })
    embeddedServer(CIO, host = configuration.bindAddress, port = configuration.httpPort) { rendezvousModule(service) }.start(wait = true)
}
