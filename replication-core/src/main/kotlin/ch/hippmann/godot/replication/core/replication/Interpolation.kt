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

    public fun push(timeMilliseconds: Long, value: T) {
        if (count > 0 && timeMilliseconds < timeAt(count - 1)) return
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
            if (extrapolation == 0L || newestTime == previousTime) return valueAt(count - 1)
            val clamped = minOf(extrapolation, maximumExtrapolationMilliseconds)
            val weight = 1.0 + clamped.toDouble() / (newestTime - previousTime)
            return interpolator.interpolate(valueAt(count - 2), valueAt(count - 1), weight)
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
    }

    private fun timeAt(offset: Int): Long = times[(start + offset) % capacity]

    @Suppress("UNCHECKED_CAST")
    private fun valueAt(offset: Int): T = values[(start + offset) % capacity] as T

    public companion object {
        public const val DEFAULT_CAPACITY: Int = 8
    }
}
