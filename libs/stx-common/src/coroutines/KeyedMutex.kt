package com.strange.common.coroutines

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock per key, so callers only wait for each other when they want the same thing.
 *
 * ```kotlin
 * private val loading = KeyedMutex<UserId>()
 *
 * suspend fun profile(id: UserId): Profile =
 *     cache.get(id) ?: loading.withLock(id) {
 *         // Checked again inside: whoever was ahead of us has already filled it in.
 *         cache.get(id) ?: fetch(id).also { cache.put(id, it) }
 *     }
 * ```
 *
 * This is the suspending-loader case [CoroutineSafeMap] refuses. A single lock across a whole map
 * turns *n* concurrent loads of *n* different keys into one queue; this leaves them concurrent and
 * serialises only the ones that would otherwise be the same work done twice — the stampede a cold
 * cache produces the moment it is asked for a popular key by everybody at once.
 *
 * **Check again inside the block.** The point of waiting is that whoever went first has probably
 * done the work already; a body that does not look is a body that does it a second time.
 *
 * A key's lock exists only while somebody wants it, so a map of every key ever seen is not left
 * behind.
 */
class KeyedMutex<K> {
    private val registry = Mutex()
    private val locks = mutableMapOf<K, Holder>()

    /** Runs [block] with [key] held, waiting only for callers holding that same key. */
    suspend fun <R> withLock(
        key: K,
        block: suspend () -> R,
    ): R {
        val holder = registry.withLock { locks.getOrPut(key) { Holder() }.also { it.waiting++ } }
        try {
            return holder.mutex.withLock { block() }
        } finally {
            registry.withLock {
                if (--holder.waiting == 0) locks.remove(key)
            }
        }
    }

    /** How many keys are held or waited on right now — for a test, or a metric. */
    suspend fun held(): Int = registry.withLock { locks.size }

    private class Holder {
        val mutex = Mutex()

        /** Holders and waiters both count: the last one out takes the lock away with it. */
        var waiting = 0
    }
}
