package com.softistx.redis.lock

import com.softistx.redis.Redis
import com.softistx.redis.RedisLockException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * A lock only its holder can release.
 *
 * `SET key token NX PX ttl` is the whole acquisition: one round trip, and the TTL is what makes a
 * holder that dies mid-task recoverable — there is nobody left to release it, so the lock has to let
 * go on its own. Release and [extend] run as Lua so the token check and the write cannot be
 * separated; see [RELEASE_IF_HELD].
 *
 * ```kotlin
 * RedisLock(redis, "invoice:42").withLock(wait = 5.seconds) { chargeCard() }
 * ```
 *
 * **What this is not.** It is a lock on one Redis, not Redlock across several. If that Redis fails
 * over to a replica that had not yet received the `SET`, two holders can believe they have it. That
 * is the standard trade, and it is fine for what a lock is usually for here — keeping one scheduled
 * job from running twice, serialising a cache rebuild — and not fine as the only thing standing
 * between two writers and a corrupted invoice. For that, the writer needs its own conditional write.
 */
class RedisLock(
    private val redis: Redis,
    val name: String,
    val ttl: Duration = 30.seconds,
) {
    val key: String get() = redis.key("lock", name)

    /** The token that owns the lock, or null if somebody else does. */
    suspend fun tryAcquire(): String? {
        val token = UUID.randomUUID().toString()
        val set = redis.commands.set(key, token, SetArgs.Builder.nx().px(ttl.inWholeMilliseconds))
        return token.takeIf { set == "OK" }
    }

    /** Frees the lock if [token] still holds it. False means it had already moved on. */
    suspend fun release(token: String): Boolean =
        redis.commands.eval<Long>(RELEASE_IF_HELD, ScriptOutputType.INTEGER, arrayOf(key), token) == 1L

    /** Puts the full [ttl] back, if [token] still holds the lock. */
    suspend fun extend(
        token: String,
        ttl: Duration = this.ttl,
    ): Boolean =
        redis.commands.eval<Long>(
            EXTEND_IF_HELD,
            ScriptOutputType.INTEGER,
            arrayOf(key),
            token,
            ttl.inWholeMilliseconds.toString(),
        ) == 1L

    suspend fun isHeld(): Boolean = redis.commands.get(key) != null

    /**
     * Runs [block] holding the lock, and releases it however [block] ends.
     *
     * Waits up to [wait] to get in, retrying every [retry] — polling, and not a blocking pop, because
     * a lock has no queue to pop from. Throws [RedisLockException] if the wait runs out — a caller
     * that would rather skip the work uses [withLockOrNull].
     *
     * While [block] runs, a watchdog puts the TTL back every third of it. Without that the lock
     * would be a deadline on the work rather than a lock: a `block` slower than [ttl] loses it
     * silently, and a second holder starts on the same task. Turn it off with `renew = false` where
     * the work must not outlive the TTL.
     */
    suspend fun <T> withLock(
        wait: Duration = Duration.ZERO,
        retry: Duration = 50.milliseconds,
        renew: Boolean = true,
        block: suspend () -> T,
    ): T =
        withLockOrNull(wait, retry, renew) { block() }
            ?: throw RedisLockException("'$name' is held by somebody else, and did not come free within $wait")

    /** [withLock], answering null instead of throwing when the lock never came free. */
    suspend fun <T> withLockOrNull(
        wait: Duration = Duration.ZERO,
        retry: Duration = 50.milliseconds,
        renew: Boolean = true,
        block: suspend () -> T,
    ): T? {
        val token = acquire(wait, retry) ?: return null
        return coroutineScope {
            val watchdog =
                if (renew) {
                    launch {
                        while (isActive) {
                            delay(ttl / 3)
                            if (!extend(token)) break
                        }
                    }
                } else {
                    null
                }
            try {
                block()
            } finally {
                watchdog?.cancel()
                release(token)
            }
        }
    }

    /** Tries now, then every [retry] until [wait] is up — so the last attempt lands within one retry of it. */
    private suspend fun acquire(
        wait: Duration,
        retry: Duration,
    ): String? {
        val deadline = TimeSource.Monotonic.markNow() + wait
        while (true) {
            tryAcquire()?.let { return it }
            if (deadline.hasPassedNow()) return null
            delay(retry)
        }
    }
}
