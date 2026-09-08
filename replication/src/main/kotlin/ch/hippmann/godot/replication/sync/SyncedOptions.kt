package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.codec.Precision
import ch.hippmann.godot.replication.core.codec.Quantization
import ch.hippmann.godot.replication.core.replication.Interpolator
import ch.hippmann.godot.replication.core.replication.PropertyCodec
import ch.hippmann.godot.replication.core.replication.SyncMode

class SyncedOptions<T> @PublishedApi internal constructor() {
    @PublishedApi internal var reliable: Boolean = true
    @PublishedApi internal var mode: SyncMode = SyncMode.OnChange
    @PublishedApi internal var quantization: Quantization = Quantization.None
    @PublishedApi internal var precision: Precision = Precision.Single
    @PublishedApi internal var interpolated: Boolean = false
    @PublishedApi internal var interpolator: Interpolator<T>? = null
    @PublishedApi internal var codec: PropertyCodec<T>? = null
    @PublishedApi internal var onRemoteChange: ((old: T, new: T) -> Unit)? = null
    @PublishedApi internal var interestFilter: InterestFilter? = null

    fun reliable() {
        reliable = true
    }

    fun unreliable() {
        reliable = false
    }

    fun onChange() {
        mode = SyncMode.OnChange
    }

    /** Sends every tick divisible by [rate] while the value keeps changing; [rate] 0 means every tick. */
    fun continuous(rate: Int = 0, idleAfterTicks: Int = 10) {
        mode = SyncMode.Continuous(rate, idleAfterTicks)
    }

    fun quantize(quantization: Quantization) {
        this.quantization = quantization
    }

    fun precision(precision: Precision) {
        this.precision = precision
    }

    fun interpolate(interpolator: Interpolator<T>? = null) {
        interpolated = true
        this.interpolator = interpolator
    }

    fun codec(codec: PropertyCodec<T>) {
        this.codec = codec
    }

    fun onRemoteChange(block: (old: T, new: T) -> Unit) {
        onRemoteChange = block
    }

    /** Restricts this property's stream further than the node's filter. */
    fun interest(filter: InterestFilter) {
        interestFilter = filter
    }
}
