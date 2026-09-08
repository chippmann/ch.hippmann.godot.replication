package ch.hippmann.godot.replication.core.simulation

public data class NetworkConditions(
    val latencyMilliseconds: Long,
    val jitterMilliseconds: Long = 0,
    val lossPercent: Double = 0.0,
    val seed: Long = 42,
) {
    init {
        require(latencyMilliseconds >= 0 && jitterMilliseconds >= 0) { "Latency and jitter must not be negative" }
        require(lossPercent in 0.0..100.0) { "Loss is a percentage" }
    }
}
