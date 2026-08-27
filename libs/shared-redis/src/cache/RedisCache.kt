package com.strange.redis.cache

import com.strange.redis.Redis
import com.strange.redis.codec.ValueCodec
import com.strange.redis.deleteKeys
import io.lettuce.core.SetArgs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A typed cache over one keyspace prefix.
 *
 * ```kotlin
 * val sessions = RedisCache(redis, "sessions", ValueCodec.json<Session>(), ttl = 30.minutes)
 * val session = sessions.getOrLoad(id) { database.loadSession(id) }
 * ```
 *
 * **[getOrLoad] is not single-flight.** Ten requests missing the same key call [load] ten times, and
 * that is on purpose: making it single-flight means taking a lock on every miss, which costs two
 * round trips on the path that is supposed to be fast and turns a cache into a coordination point.
 * When a load is expensive enough that the stampede matters, wrap it in a `RedisLock` at the call
 * site, where the cost is a decision rather than a default.
 *
 * **A null is an absence, not a cached value.** Caching "this does not exist" is a real technique
 * against a lookup storm on missing ids, and it needs a codec for a nullable type rather than a
 * special case here — `RedisCache<Session?>` with a codec that encodes null.
 */
class RedisCache<T>(
    private val redis: Redis,
    val name: String,
    private val codec: ValueCodec<T>,
    private val ttl: Duration? = null,
) {
    fun key(id: String): String = redis.key(name, id)

    suspend fun get(id: String): T? = redis.commands.get(key(id))?.let(codec::decode)

    /** Every id that is cached, in the order asked for. One `MGET`, not one `GET` per id. */
    suspend fun getAll(ids: Collection<String>): Map<String, T> {
        if (ids.isEmpty()) return emptyMap()
        val order = ids.toList()
        return redis.commands
            .mget(*order.map(::key).toTypedArray())
            .toList()
            .withIndex()
            .filter { (_, entry) -> entry.hasValue() }
            .associate { (index, entry) -> order[index] to codec.decode(entry.value) }
    }

    suspend fun put(
        id: String,
        value: T,
        ttl: Duration? = this.ttl,
    ) {
        val encoded = codec.encode(value)
        if (ttl == null) {
            redis.commands.set(key(id), encoded)
        } else {
            redis.commands.set(key(id), encoded, SetArgs.Builder.px(ttl.inWholeMilliseconds))
        }
    }

    /**
     * Writes every entry, concurrently.
     *
     * Not `MSET`, which cannot carry a TTL and would leave the entries to live forever. Lettuce
     * multiplexes over one connection, so the concurrent writes go out as a pipeline and cost one
     * round trip between them rather than one each.
     */
    suspend fun putAll(
        values: Map<String, T>,
        ttl: Duration? = this.ttl,
    ) = coroutineScope {
        values.map { (id, value) -> async { put(id, value, ttl) } }.awaitAll()
        Unit
    }

    /** The cached value, or [load]'s, which is cached on the way back. */
    suspend fun getOrLoad(
        id: String,
        ttl: Duration? = this.ttl,
        load: suspend () -> T,
    ): T = get(id) ?: load().also { put(id, it, ttl) }

    suspend fun contains(id: String): Boolean = (redis.commands.exists(key(id)) ?: 0L) > 0

    /** How much longer this entry has, or null when it has no expiry or is not there. */
    suspend fun expiresIn(id: String): Duration? =
        redis.commands
            .pttl(key(id))
            ?.takeIf { it >= 0 }
            ?.milliseconds

    /** Whether there was anything to invalidate. */
    suspend fun invalidate(id: String): Boolean = (redis.commands.del(key(id)) ?: 0L) > 0

    /** Everything under this cache's prefix, and how many that was. */
    suspend fun invalidateAll(): Long = redis.deleteKeys("${redis.key(name)}:*")
}
