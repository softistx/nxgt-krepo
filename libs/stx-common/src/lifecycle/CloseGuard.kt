package com.softistx.common.lifecycle

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs a close once, however many callers reach it and however many times.
 *
 * ```kotlin
 * class Redis internal constructor(private val client: RedisClient, …) : AutoCloseable {
 *     private val guard = CloseGuard()
 *
 *     override fun close() = guard.once { connection.close(); client.shutdown() }
 * }
 * ```
 *
 * **A resource that is handed around is closed more than once, and that has to be harmless.** Ktor's
 * dependency injection closes every `AutoCloseable` it hands out when the application stops — one it
 * merely passed through included — and a plugin, a container and the code that built the thing all
 * have a reasonable claim to closing it. Ownership rules say who *should*; this says what happens
 * when two of them do, which is nothing.
 *
 * Idempotence is not something the underlying clients agree on. Lettuce's `shutdown` is quiet the
 * second time, the RabbitMQ client throws `AlreadyClosedException`, and a driver is free to change
 * its mind between versions — so this does not depend on any of them.
 *
 * The flag is an [AtomicBoolean] and not a `Mutex`, deliberately: `close()` is an ordinary blocking
 * function that a shutdown hook, a `use` block or a container's teardown may call from a thread
 * that cannot suspend. See the concurrency notes in this library's README for which of the shared
 * types fits which caller.
 */
class CloseGuard {
    private val closed = AtomicBoolean(false)

    /** Whether a close has been claimed — true from the moment the first caller wins, not when it finishes. */
    val isClosed: Boolean get() = closed.get()

    /** Runs [block] if this is the first call, and does nothing on every call after it. */
    fun once(block: () -> Unit) {
        if (closed.compareAndSet(false, true)) block()
    }
}
