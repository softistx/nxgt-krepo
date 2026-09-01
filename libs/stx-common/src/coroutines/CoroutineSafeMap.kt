package com.softistx.common.coroutines

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A map guarded by a `Mutex` instead of by a lock.
 *
 * ```kotlin
 * private val sessions = CoroutineSafeMap<UserId, Session>()
 *
 * sessions.put(id, session)
 * sessions.getOrPut(id) { Session.empty(id) }
 * ```
 *
 * A `Mutex` suspends where a `synchronized` block or a `ConcurrentHashMap`'s internal lock parks a
 * thread, so this is the right shape when every caller is a coroutine — which is most of the code
 * in this repo.
 *
 * **It is the wrong shape when a caller is not.** Nothing here can be reached from a Java callback,
 * a listener or any thread that cannot suspend: there is no non-suspending way in, and
 * `runBlocking` on a library's own I/O thread is worse than the concurrent map it replaced. That
 * boundary is what [Mailbox] is for.
 *
 * **[getOrPut] takes a value, not a loader.** A suspending default would run while the lock is
 * held, so one slow load would block every other key — the mistake this signature is shaped to make
 * impossible. When the default has to suspend, the type that fits is [KeyedMutex], which locks per
 * key rather than across the map.
 *
 * Each call is atomic on its own; a sequence of them is not. Use [update] for anything that reads
 * and then writes based on what it read.
 */
class CoroutineSafeMap<K, V>(
    initial: Map<K, V> = emptyMap(),
) {
    private val map = initial.toMutableMap()
    private val mutex = Mutex()

    suspend fun get(key: K): V? = mutex.withLock { map[key] }

    suspend fun put(
        key: K,
        value: V,
    ): V? = mutex.withLock { map.put(key, value) }

    suspend fun remove(key: K): V? = mutex.withLock { map.remove(key) }

    suspend fun containsKey(key: K): Boolean = mutex.withLock { key in map }

    /** The value for [key], inserting [default] first if there is none. */
    suspend fun getOrPut(
        key: K,
        default: () -> V,
    ): V = mutex.withLock { map.getOrPut(key, default) }

    /** A copy, safe to iterate — the map itself is never handed out. */
    suspend fun snapshot(): Map<K, V> = mutex.withLock { map.toMap() }

    suspend fun size(): Int = mutex.withLock { map.size }

    suspend fun isEmpty(): Boolean = mutex.withLock { map.isEmpty() }

    suspend fun clear() {
        mutex.withLock { map.clear() }
    }

    /**
     * Runs [block] against the map with the lock held, for the compound operations that are only
     * correct as one step — read-then-write, or several keys that have to agree.
     *
     * [block] does not suspend, on purpose: it is holding the lock, and every other caller is
     * waiting behind whatever it does.
     */
    suspend fun <R> update(block: (MutableMap<K, V>) -> R): R = mutex.withLock { block(map) }
}
