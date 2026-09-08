package ch.hippmann.godot.replication.core.simulation

import kotlin.random.Random

/** Holds items until their simulated arrival time; deterministic for a given seed so tests can reproduce runs. */
public class DelayQueue<T>(private val conditions: NetworkConditions) {
    private class Entry<T>(val releaseTime: Long, val sequence: Long, val item: T)

    private val random = Random(conditions.seed)
    private val entries = java.util.PriorityQueue<Entry<T>>(compareBy({ entry -> entry.releaseTime }, { entry -> entry.sequence }))
    private var sequence = 0L

    public val size: Int
        get() = entries.size

    /** Returns false when the item was dropped; [droppable] items are subject to the loss percentage. */
    public fun offer(nowMilliseconds: Long, item: T, droppable: Boolean): Boolean {
        if (droppable && conditions.lossPercent > 0.0 && random.nextDouble() * 100.0 < conditions.lossPercent) return false
        val jitter = if (conditions.jitterMilliseconds > 0) random.nextLong(-conditions.jitterMilliseconds, conditions.jitterMilliseconds + 1) else 0L
        val delay = (conditions.latencyMilliseconds + jitter).coerceAtLeast(0)
        entries.add(Entry(nowMilliseconds + delay, sequence++, item))
        return true
    }

    public fun drain(nowMilliseconds: Long, action: (T) -> Unit) {
        while (true) {
            val head = entries.peek() ?: return
            if (head.releaseTime > nowMilliseconds) return
            entries.poll()
            action(head.item)
        }
    }

    public fun clear(): Unit = entries.clear()
}
