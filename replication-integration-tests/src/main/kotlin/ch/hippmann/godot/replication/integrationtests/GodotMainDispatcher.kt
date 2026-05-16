package ch.hippmann.godot.replication.integrationtests

import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine dispatcher that queues continuations to be drained from Godot's main
 * thread via [drain]. Without this, kotlinx-coroutines resumes continuations on its
 * own DefaultExecutor / EventLoop thread, which makes Godot reject Node and
 * Multiplayer calls with "can only be manipulated from the main thread".
 */
class GodotMainDispatcher : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.offer(block)
    }

    /** Run all currently-queued continuations. Call from `_process`. */
    fun drain() {
        var task = queue.poll()
        while (task != null) {
            try {
                task.run()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            task = queue.poll()
        }
    }
}
