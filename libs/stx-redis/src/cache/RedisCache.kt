package com.softistx.redis.cache

import com.softistx.redis.Redis
import com.softistx.redis.codec.JsonValueCodec
import com.softistx.redis.codec.ValueCodec
import com.softistx.redis.deleteKeys
import io.lettuce.core.SetArgs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A typed cache over one keyspace prefix.
 *
 * ```kotlin
 * val sessions = redis.cache<Session>("sessions", ttl = 30.minutes)
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
 * against a lookup storm on missing ids, and it belongs in the type rather than in a special case
 * here — `redis.cache<Session?>("sessions")`, whose serializer encodes null.
 *
 * The constructor taking a `ValueCodec` is the escape hatch for a value that must not be JSON; see
 * [ValueCodec]. Everything else should come through [cache].
 */
class RedisCache<T>(
    private val redis: Redis,
    val name: String,
    private val codec: ValueCodec<T>,
    private val ttl: Duration? = null,
) {
    /**
     * The same cache, named by its serializer — for a call site whose `T` cannot be reified.
     * [cache] is the one to reach for otherwise.
     */
    constructor(
        redis: Redis,
        name: String,
        serializer: KSerializer<T>,
        ttl: Duration? = null,
        json: Json = redis.json,
    ) : this(redis, name, JsonValueCodec(json, serializer), ttl)

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
    suspend fun invalidateAll(): Long = redis.deleteKeys("$name:*")
}

/**
 * A cache of `T`, serialized with kotlinx.serialization through this connection's `Json`.
 *
 * ```kotlin
 * val sessions = redis.cache<Session>("sessions", ttl = 30.minutes)
 * ```
 *
 * [ttl] is what entries get when [RedisCache.put] is not told otherwise; null means they stay until
 * something invalidates them, which for a cache is a decision worth making on purpose.
 */
inline fun <reified T> Redis.cache(
    name: String,
    ttl: Duration? = null,
    json: Json = this.json,
): RedisCache<T> = RedisCache(this, name, ValueCodec.json<T>(json), ttl)
