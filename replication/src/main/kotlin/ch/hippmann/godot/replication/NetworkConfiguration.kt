package ch.hippmann.godot.replication

import godot.api.Node

class NetworkConfiguration {
    /** UDP port a host binds; joiners use an ephemeral port unless [joinPort] is set. */
    var port: Int = 7777
    var joinPort: Int = 0
    var discoveryPort: Int = 7778
    var enableDiscovery: Boolean = true
    var tickRate: Int = 30
    var rpcChannels: Int = 1
    var interpolationDelayMilliseconds: Int = 100
    var connectTimeoutMilliseconds: Long = 5_000
    var joinTimeoutMilliseconds: Long = 15_000
    var meshTimeoutMilliseconds: Long = 10_000
    var snapshotTimeoutMilliseconds: Long = 10_000
    var linkTimeoutMilliseconds: Int = 6_000
    var verboseTransportLogging: Boolean = false
    /** Where the library adds the level node named "Level"; every member must use the same path. */
    var levelParentPath: String = "/root"
    /** Runs after a level was added and before this member reports it loaded, for asset warm up and the like. */
    var levelPreparation: (suspend (level: Node) -> Unit)? = null

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
    }
}
