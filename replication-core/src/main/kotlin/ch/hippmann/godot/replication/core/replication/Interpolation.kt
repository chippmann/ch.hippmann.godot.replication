package ch.hippmann.godot.replication.core.replication

public fun interface Interpolator<T> {
    public fun interpolate(from: T, to: T, weight: Double): T
}

/** Time stamped samples of one property; [sample] renders slightly in the past so movement stays smooth. */
public class InterpolationBuffer<T>(private val capacity: Int = DEFAULT_CAPACITY) {
    private val times = LongArray(capacity)
    private val values = arrayOfNulls<Any?>(capacity)
    private var count = 0
    private var start = 0

    public val latest: T?
        get() = if (count == 0) null else valueAt(count - 1)

    public val isEmpty: Boolean
        get() = count == 0

    private var largestGapMilliseconds = 0.0

    /** Delay that keeps rendering behind the gaps this stream actually showed lately; jitter and loss raise it, calm streams let it sink. */
    public val recommendedDelayMilliseconds: Long
        get() = minOf((largestGapMilliseconds * GAP_SAFETY_FACTOR).toLong(), MAXIMUM_RECOMMENDED_DELAY_MILLISECONDS)

    public fun push(timeMilliseconds: Long, value: T) {
        if (count > 0 && timeMilliseconds < timeAt(count - 1)) return
        if (count > 0) {
            val gap = (timeMilliseconds - timeAt(count - 1)).toDouble()
            largestGapMilliseconds = maxOf(gap, largestGapMilliseconds * GAP_DECAY)
        }
        if (count == capacity) {
            start = (start + 1) % capacity
            count--
        }
        val index = (start + count) % capacity
        times[index] = timeMilliseconds
        values[index] = value
        count++
    }

    public fun sample(renderTimeMilliseconds: Long, interpolator: Interpolator<T>, maximumExtrapolationMilliseconds: Long): T? {
        if (count == 0) return null
        if (count == 1 || renderTimeMilliseconds <= timeAt(0)) return valueAt(0)
        val newestTime = timeAt(count - 1)
        if (renderTimeMilliseconds >= newestTime) {
            val extrapolation = renderTimeMilliseconds - newestTime
            val previousTime = timeAt(count - 2)
            if (extrapolation == 0L || newestTime == previousTime || maximumExtrapolationMilliseconds <= 0L) return valueAt(count - 1)
            val forward = minOf(extrapolation, maximumExtrapolationMilliseconds)
            val extrapolated = interpolator.interpolate(valueAt(count - 2), valueAt(count - 1), 1.0 + forward.toDouble() / (newestTime - previousTime))
            if (extrapolation <= maximumExtrapolationMilliseconds) return extrapolated
            // Past the allowance the guess eases back onto the last real value, so a stream that simply stopped rests where it ended.
            val back = (extrapolation - maximumExtrapolationMilliseconds).toDouble() / maximumExtrapolationMilliseconds
            return if (back >= 1.0) valueAt(count - 1) else interpolator.interpolate(extrapolated, valueAt(count - 1), back)
        }
        var index = 1
        while (timeAt(index) < renderTimeMilliseconds) index++
        val fromTime = timeAt(index - 1)
        val toTime = timeAt(index)
        val weight = if (toTime == fromTime) 1.0 else (renderTimeMilliseconds - fromTime).toDouble() / (toTime - fromTime)
        return interpolator.interpolate(valueAt(index - 1), valueAt(index), weight)
    }

    public fun clear() {
        count = 0
        start = 0
        largestGapMilliseconds = 0.0
    }

    private fun timeAt(offset: Int): Long = times[(start + offset) % capacity]

    @Suppress("UNCHECKED_CAST")
    private fun valueAt(offset: Int): T = values[(start + offset) % capacity] as T

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 8
        public const val GAP_SAFETY_FACTOR: Double = 1.5
        public const val GAP_DECAY: Double = 0.95
        public const val MAXIMUM_RECOMMENDED_DELAY_MILLISECONDS: Long = 250
    }
}
