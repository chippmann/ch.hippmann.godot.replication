package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.core.simulation.NetworkConditions

/** Applied to every link of this process in both directions; loss only affects unreliable packets. */
data class NetworkSimulation(
    val latencyMilliseconds: Long,
    val jitterMilliseconds: Long = 0,
    val lossPercent: Double = 0.0,
    val seed: Long = 42,
) {
    internal fun toConditions(): NetworkConditions = NetworkConditions(latencyMilliseconds, jitterMilliseconds, lossPercent, seed)
}
