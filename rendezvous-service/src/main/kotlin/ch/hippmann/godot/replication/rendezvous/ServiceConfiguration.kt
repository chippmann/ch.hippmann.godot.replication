package ch.hippmann.godot.replication.rendezvous

/** Everything the service needs comes from the environment; the defaults are placeholders for a local run. */
data class ServiceConfiguration(
    val bindAddress: String = "0.0.0.0",
    val httpPort: Int = 7789,
    val udpPort: Int = 7790,
    val relayPorts: IntRange = 7800..7899,
    /** Address the clients send probes and relay traffic to; empty means "whatever host they reached over HTTP". */
    val publicAddress: String = "",
    val sessionTimeToLiveSeconds: Long = 45,
    val relayIdleSeconds: Long = 120,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ServiceConfiguration {
            val defaults = ServiceConfiguration()
            val range = environment["RENDEZVOUS_RELAY_PORT_RANGE"]?.split("-")?.map { it.trim().toInt() }
            return ServiceConfiguration(
                bindAddress = environment["RENDEZVOUS_BIND_ADDRESS"] ?: defaults.bindAddress,
                httpPort = environment["RENDEZVOUS_HTTP_PORT"]?.toInt() ?: defaults.httpPort,
                udpPort = environment["RENDEZVOUS_UDP_PORT"]?.toInt() ?: defaults.udpPort,
                relayPorts = if (range != null && range.size == 2) range[0]..range[1] else defaults.relayPorts,
                publicAddress = environment["RENDEZVOUS_PUBLIC_ADDRESS"] ?: defaults.publicAddress,
                sessionTimeToLiveSeconds = environment["RENDEZVOUS_SESSION_TTL_SECONDS"]?.toLong() ?: defaults.sessionTimeToLiveSeconds,
                relayIdleSeconds = environment["RENDEZVOUS_RELAY_IDLE_SECONDS"]?.toLong() ?: defaults.relayIdleSeconds,
            )
        }
    }
}
