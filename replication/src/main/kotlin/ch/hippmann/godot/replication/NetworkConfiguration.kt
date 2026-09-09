package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.transport.ConnectionStrategy
import ch.hippmann.godot.replication.transport.DirectStrategy
import ch.hippmann.godot.replication.transport.PunchthroughStrategy
import ch.hippmann.godot.replication.transport.RelayStrategy
import godot.api.Node

class NetworkConfiguration {
    /** UDP port a host binds; joiners use an ephemeral port unless [joinPort] is set. */
    var port: Int = 7777
    var joinPort: Int = 0
    var discoveryPort: Int = 7778
    var enableDiscovery: Boolean = true
    var tickRate: Int = 30
    var rpcChannels: Int = 1
    /** Least delay behind the newest sample for interpolated values; 0 means two ticks of [tickRate]. A gappy stream raises its own delay. */
    var interpolationDelayMilliseconds: Int = 0
    var connectTimeoutMilliseconds: Long = 5_000
    var joinTimeoutMilliseconds: Long = 15_000
    var meshTimeoutMilliseconds: Long = 10_000
    var snapshotTimeoutMilliseconds: Long = 10_000
    var linkTimeoutMilliseconds: Int = 6_000
    var verboseTransportLogging: Boolean = false
    /** Base URL of a rendezvous service; null keeps sessions on the LAN (discovery and typed addresses only). */
    var rendezvousUrl: String? = null
    var punchTimeoutMilliseconds: Long = 4_000
    var relayTimeoutMilliseconds: Long = 6_000
    /** Lets punchthrough try private addresses too; only useful to exercise it on one machine. */
    var punchPrivateAddresses: Boolean = false
    /** Strategy names in the order to try them; "direct", "punchthrough" and "relay" exist. */
    var connectionStrategies: List<String> = listOf("direct", "punchthrough", "relay")
    /** Where the library adds the level node named "Level"; every member must use the same path. */
    var levelParentPath: String = "/root"
    /** Runs after a level was added and before this member reports it loaded, for asset warm up and the like. */
    var levelPreparation: (suspend (level: Node) -> Unit)? = null

    val effectiveInterpolationDelayMilliseconds: Int
        get() = if (interpolationDelayMilliseconds > 0) interpolationDelayMilliseconds else 2_000 / maxOf(1, tickRate)

    fun copy(): NetworkConfiguration = NetworkConfiguration().also { copy ->
        copy.port = port
        copy.joinPort = joinPort
        copy.discoveryPort = discoveryPort
        copy.enableDiscovery = enableDiscovery
        copy.tickRate = tickRate
        copy.rpcChannels = rpcChannels
        copy.interpolationDelayMilliseconds = interpolationDelayMilliseconds
        copy.connectTimeoutMilliseconds = connectTimeoutMilliseconds
        copy.joinTimeoutMilliseconds = joinTimeoutMilliseconds
        copy.meshTimeoutMilliseconds = meshTimeoutMilliseconds
        copy.snapshotTimeoutMilliseconds = snapshotTimeoutMilliseconds
        copy.linkTimeoutMilliseconds = linkTimeoutMilliseconds
        copy.verboseTransportLogging = verboseTransportLogging
        copy.levelParentPath = levelParentPath
        copy.levelPreparation = levelPreparation
        copy.rendezvousUrl = rendezvousUrl
        copy.punchTimeoutMilliseconds = punchTimeoutMilliseconds
        copy.relayTimeoutMilliseconds = relayTimeoutMilliseconds
        copy.connectionStrategies = connectionStrategies
        copy.punchPrivateAddresses = punchPrivateAddresses
    }

    internal fun strategies(): List<ConnectionStrategy> = connectionStrategies.map { name ->
        when (name) {
            DirectStrategy.name -> DirectStrategy
            PunchthroughStrategy.name -> PunchthroughStrategy
            RelayStrategy.name -> RelayStrategy
            else -> throw IllegalArgumentException("Unknown connection strategy '$name'")
        }
    }

    internal fun timeoutFor(strategy: ConnectionStrategy): Long = when (strategy) {
        PunchthroughStrategy -> punchTimeoutMilliseconds
        RelayStrategy -> relayTimeoutMilliseconds
        else -> connectTimeoutMilliseconds
    }
}
