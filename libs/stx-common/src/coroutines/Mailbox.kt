package com.softistx.common.coroutines

import kotlinx.coroutines.channels.Channel

/**
 * The way into coroutine code from a thread that cannot suspend.
 *
 * ```kotlin
 * private val confirms = Mailbox<Confirm>()
 *
 * // On somebody else's thread — a Java listener, a callback, a driver's dispatcher:
 * client.addConfirmListener { tag, multiple -> confirms.post(Acked(tag, multiple)) }
 *
 * // In one coroutine, which owns everything the messages touch:
 * confirms.consume { message -> apply(message) }
 * ```
 *
 * **This is the only correct bridge, and the reason is worth stating.** A `Mutex` cannot be taken
 * from a callback, because taking it may suspend and a callback cannot; a `runBlocking` around it
 * parks a thread the library needs for its own I/O, which is how a client deadlocks against itself.
 * What is left is a queue that can be written to without blocking or suspending — [post] is
 * `trySend` on an unbounded channel, which always succeeds and never waits.
 *
 * **What it buys is bigger than thread safety.** One consumer means the state the messages touch has
 * a single owner and needs no synchronisation at all: plain `var`s, plain maps, no `@Volatile`. And
 * because a channel is FIFO, two messages that must be applied in order *are* applied in order —
 * which a pair of concurrent flags cannot promise however carefully each one is guarded.
 *
 * Unbounded on purpose: the producer is a thread that must not be made to wait, so the only
 * question is whether the consumer keeps up, and a bound would answer it by losing messages.
 */
class Mailbox<T> : AutoCloseable {
    private val messages = Channel<T>(Channel.UNLIMITED)

    /**
     * Puts a message in the queue. Never blocks, never suspends, safe from any thread.
     *
     * Answers `false` only when the mailbox is closed — which means the consumer has gone and the
     * message will not be applied by anyone.
     */
    fun post(message: T): Boolean = messages.trySend(message).isSuccess

    /**
     * The next message, or null when there is not one waiting.
     *
     * For a consumer that is already in a loop of its own — polling a client, say — and wants to
     * apply what has arrived without giving up its turn.
     */
    fun tryReceive(): T? = messages.tryReceive().getOrNull()

    /**
     * Hands every message to [block], in order, until the mailbox is closed and drained.
     *
     * Run this in exactly one coroutine. Two consumers would each see some of the messages, which
     * is every reason to use this class undone at once.
     */
    suspend fun consume(block: suspend (T) -> Unit) {
        for (message in messages) block(message)
    }

    /**
     * Stops new messages, leaving what is already queued to be consumed.
     *
     * A [consume] loop therefore finishes its backlog and *then* returns, which is what lets a
     * shutdown answer the callers still waiting on it rather than abandoning them.
     *
     * **`use` is rarely the right way to call this.** A mailbox usually belongs to an object whose
     * lifetime it shares — held as a field, closed from that object's own `close` — because the
     * producer is somebody else's thread and the consumer is a coroutine, and neither is a block.
     * `use` fits only when one block owns the whole exchange, posts included; anywhere else it ends
     * the consumer while the producer is still running.
     */
    override fun close() {
        messages.close()
    }
}
