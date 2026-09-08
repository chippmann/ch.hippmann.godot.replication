package ch.hippmann.godot.replication.core.diagnostics

/** Accumulates within a window and publishes the finished window; readers only ever see whole windows. */
public class Counters(private val windowMilliseconds: Long = 1_000) {
    private val current = HashMap<String, Long>()
    private var windowStart = 0L

    public var lastWindow: Map<String, Long> = emptyMap()
        private set

    public fun add(key: String, amount: Long = 1) {
        current[key] = (current[key] ?: 0L) + amount
    }

    /** Returns true when a window just closed. */
    public fun advance(nowMilliseconds: Long): Boolean {
        if (windowStart == 0L) windowStart = nowMilliseconds
        if (nowMilliseconds - windowStart < windowMilliseconds) return false
        lastWindow = HashMap(current)
        current.clear()
        windowStart = nowMilliseconds
        return true
    }

    public operator fun get(key: String): Long = lastWindow[key] ?: 0L
}
