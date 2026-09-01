package com.strange.common.concurrent

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * An object that is not thread-safe, and the lock that makes it so, as one thing.
 *
 * ```kotlin
 * private val format = Guarded(MessageFormat(pattern, locale))   // ICU documents this as unsynchronized
 *
 * fun format(arguments: Map<String, Any>): String = format.withLock { it.format(arguments) }
 * ```
 *
 * ## What it removes
 *
 * A `ReentrantLock` in one field and the thing it guards in another is a convention, and a
 * convention is checked by whoever remembers it. Nothing stops the fourth caller from reading the
 * field directly, and nothing about that read looks wrong — the failure is a rare, unreproducible
 * corruption in an object that was never meant to be shared.
 *
 * Here the value is **never exposed**. The only way to it is [withLock], so "held while touched" is
 * a fact about the type rather than a rule about the reader.
 *
 * ## Why the block does not suspend, and why that is the feature
 *
 * A `ReentrantLock` belongs to a *thread*. A coroutine that suspends inside one may resume on
 * another thread, and then `unlock` throws `IllegalMonitorStateException` — or worse, does not,
 * because a second coroutine had meanwhile been scheduled onto the original thread and re-entered
 * the lock it appeared to already hold. Neither failure looks like a locking bug when it lands.
 *
 * [withLock] takes an ordinary `(T) -> R`, so **there is no way to suspend inside it**. When the
 * work under the lock has to suspend, this is the wrong type and `kotlinx.coroutines.sync.Mutex` is
 * the right one — it is owned by a coroutine rather than by a thread, which is the whole difference.
 *
 * ## What Kotlin already gives you
 *
 * `kotlin.concurrent.withLock` wraps a bare `Lock` in a block, and this is built on it. The addition
 * is ownership, not the call: a `Lock` you hold yourself guards whatever you remember to guard.
 *
 * ## Two things worth knowing about the lock underneath
 *
 * It is **reentrant**, so a nested [withLock] on the same instance from the same thread proceeds
 * rather than deadlocking — which also means a block that calls back into its own guard can observe
 * the value halfway through a compound update. It is **unfair**, the JDK default, which trades
 * arrival order for throughput; a guard held briefly by many threads is the case that pays for it.
 */
class Guarded<T : Any>(
    private val value: T,
) {
    private val lock = ReentrantLock()

    /** Runs [block] on the guarded value with the lock held, and returns what it returned. */
    fun <R> withLock(block: (T) -> R): R = lock.withLock { block(value) }

    /**
     * Runs [block] only if the lock is free, and answers whether it ran.
     *
     * For work that is worth doing and not worth waiting for — dumping counters, refreshing a
     * cached snapshot — where a caller that finds the guard busy should carry on rather than queue
     * behind whoever has it.
     */
    fun tryWithLock(block: (T) -> Unit): Boolean {
        if (!lock.tryLock()) return false
        try {
            block(value)
        } finally {
            lock.unlock()
        }
        return true
    }

    /** Whether anybody holds it right now — for a metric or a test, never for a decision. */
    val isHeld: Boolean get() = lock.isLocked
}
