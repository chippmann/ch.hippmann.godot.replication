package ch.hippmann.godot.replication.core.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DelayQueueTest {

    @Test
    fun `items come out after the latency in order`() {
        val queue = DelayQueue<String>(NetworkConditions(latencyMilliseconds = 100))
        queue.offer(1000, "first", droppable = false)
        queue.offer(1010, "second", droppable = false)
        val early = mutableListOf<String>()
        queue.drain(1050) { early += it }
        assertEquals(emptyList(), early)
        val released = mutableListOf<String>()
        queue.drain(1105) { released += it }
        assertEquals(listOf("first"), released)
        queue.drain(1200) { released += it }
        assertEquals(listOf("first", "second"), released)
    }

    @Test
    fun `loss only touches droppable items and is deterministic per seed`() {
        fun run(seed: Long): List<Boolean> {
            val queue = DelayQueue<Int>(NetworkConditions(0, lossPercent = 50.0, seed = seed))
            return (1..100).map { index -> queue.offer(0, index, droppable = true) }
        }
        val kept = run(7).count { it }
        assertTrue(kept in 30..70, "about half survive, got $kept")
        assertEquals(run(7), run(7))
        val reliable = DelayQueue<Int>(NetworkConditions(0, lossPercent = 100.0))
        assertTrue(reliable.offer(0, 1, droppable = false))
    }

    @Test
    fun `jitter stays within bounds`() {
        val queue = DelayQueue<Int>(NetworkConditions(latencyMilliseconds = 100, jitterMilliseconds = 20, seed = 3))
        repeat(50) { index -> queue.offer(0, index, droppable = false) }
        var released = 0
        queue.drain(79) { released++ }
        assertEquals(0, released)
        queue.drain(121) { released++ }
        assertEquals(50, released)
    }
}
