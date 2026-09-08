package ch.hippmann.godot.replication.diagnostics

import ch.hippmann.godot.replication.core.session.PlayerId

/** One second window; [roundTripMilliseconds] and [packetLoss] come from ENet per connected peer. */
data class NetworkStatistics(
    val packetsIn: Long = 0,
    val packetsOut: Long = 0,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val statePacketsOut: Long = 0,
    val reliableStatePacketsOut: Long = 0,
    val ticks: Long = 0,
    val activeReplicas: Int = 0,
    val droppedEntries: Long = 0,
    val roundTripMilliseconds: Map<PlayerId, Double> = emptyMap(),
    val packetLoss: Map<PlayerId, Double> = emptyMap(),
) {
    companion object {
        const val PACKETS_IN = "packets.in"
        const val PACKETS_OUT = "packets.out"
        const val BYTES_IN = "bytes.in"
        const val BYTES_OUT = "bytes.out"
        const val STATE_PACKETS_OUT = "state.packets.out"
        const val RELIABLE_STATE_PACKETS_OUT = "state.reliable.packets.out"
        const val TICKS = "ticks"
        const val DROPPED_ENTRIES = "dropped.entries"
    }
}
