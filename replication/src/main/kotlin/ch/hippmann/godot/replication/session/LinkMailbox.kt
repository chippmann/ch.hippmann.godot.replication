package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.JoinFailure
import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.core.wire.Rejected
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.transport.EnetLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Lets a flow suspend until a specific message type arrives on a link; a [Rejected] fails every waiter on that link. */
internal class LinkMailbox(private val defer: (() -> Unit) -> Unit) {
    private class Waiter(val types: Set<MessageType>, val deferred: CompletableDeferred<WireMessage>)

    private val waiters = HashMap<Long, MutableList<Waiter>>()

    suspend fun await(link: EnetLink, types: Set<MessageType>, timeoutMilliseconds: Long): WireMessage {
        val waiter = Waiter(types, CompletableDeferred())
        waiters.getOrPut(link.key) { mutableListOf() }.add(waiter)
        try {
            return withTimeoutOrNull(timeoutMilliseconds) { waiter.deferred.await() }
                ?: throw JoinFailure.Timeout("Waited ${timeoutMilliseconds}ms for ${types.joinToString()} from $link")
        } finally {
            waiters[link.key]?.remove(waiter)
        }
    }

    suspend inline fun <reified T : WireMessage> await(link: EnetLink, type: MessageType, timeoutMilliseconds: Long): T =
        await(link, setOf(type), timeoutMilliseconds) as T

    fun deliver(link: EnetLink, message: WireMessage): Boolean {
        val linkWaiters = waiters[link.key] ?: return false
        if (message is Rejected) {
            val rejected = linkWaiters.toList()
            defer { rejected.forEach { waiter -> waiter.deferred.completeExceptionally(JoinFailure.Rejected(message.reason)) } }
            return rejected.isNotEmpty()
        }
        val waiter = linkWaiters.firstOrNull { waiter -> message.type in waiter.types } ?: return false
        defer { waiter.deferred.complete(message) }
        return true
    }

    fun fail(link: EnetLink, cause: Throwable) {
        val failed = waiters.remove(link.key) ?: return
        defer { failed.forEach { waiter -> waiter.deferred.completeExceptionally(cause) } }
    }

    fun clear() {
        waiters.values.flatten().forEach { waiter -> waiter.deferred.cancel() }
        waiters.clear()
    }
}
