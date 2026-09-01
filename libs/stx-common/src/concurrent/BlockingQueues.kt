package com.strange.common.concurrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.BlockingQueue

/**
 * Reads a [BlockingQueue] somebody else owns, without parking a thread that cannot be got back.
 *
 * ```kotlin
 * // `queue` came from a Java library — an executor's work queue, a driver's callback queue.
 * queue.consumeAsFlow().collect { handle(it) }
 * ```
 *
 * ## Read this before using it
 *
 * **If you own both ends of the queue, this is the wrong tool.** A `Channel` is the coroutine
 * primitive for that, and `com.strange.common.coroutines.Mailbox` is the one for a producer that
 * cannot suspend — a Java callback, a driver's thread. Neither blocks a thread at all, and the
 * argument for them is in `Mailbox`'s own documentation.
 *
 * This exists for the queue you did **not** choose: a `BlockingQueue` handed to you by a library
 * that will not take a `Channel`, and that you now have to drain from coroutine code.
 *
 * ## What it does that a naive bridge does not
 *
 * `take()` blocks, so it runs through [runInterruptible] on [Dispatchers.IO]. That is the part worth
 * having: when the collector is cancelled, the thread sitting in `take()` is **interrupted** rather
 * than left parked for ever on a queue nobody will feed again. A bridge written with a plain
 * `withContext(Dispatchers.IO) { take() }` leaks one thread per cancelled consumer, and leaks it
 * silently, because a blocked thread costs nothing visible until there are a few hundred of them.
 *
 * The flow is cold and infinite: it ends when the collector stops or the coroutine is cancelled,
 * never on its own, because a queue has no end. It costs a dispatch per element, which is the price
 * of the interruptible boundary — a path that moves enough elements for that to matter should be
 * moving them through a `Channel` instead.
 */
fun <T : Any> BlockingQueue<T>.consumeAsFlow(): Flow<T> =
    flow {
        while (true) {
            emit(runInterruptible(Dispatchers.IO) { take() })
        }
    }

/**
 * Takes up to [limit] elements that are already there, without waiting for more.
 *
 * `drainTo` is the right call and returns a count into a collection you had to make yourself; this
 * returns the elements. It takes what is queued **now** and does not block, so an empty result means
 * an empty queue at this instant rather than a closed one.
 */
fun <T : Any> BlockingQueue<T>.drain(limit: Int = Int.MAX_VALUE): List<T> {
    require(limit > 0) { "limit must be positive, was $limit" }
    val taken = ArrayList<T>(minOf(limit, size))
    drainTo(taken, limit)
    return taken
}
