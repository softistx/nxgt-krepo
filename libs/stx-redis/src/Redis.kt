package com.softistx.redis

import com.softistx.common.lifecycle.CloseGuard
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.serialization.json.Json
import java.time.Duration as JavaDuration

/**
 * A connection to Redis, and the things built on it.
 *
 * Lettuce already suspends: `RedisCoroutinesCommands` covers the whole protocol, RediSearch and
 * vector sets included, and [commands] hands it over unwrapped. This class exists for what Lettuce
 * deliberately leaves open — one place that owns the client's lifecycle, and one place that decides
 * what a key looks like.
 *
 * Values are strings on the wire, not bytes. Every typed layer here serializes with
 * kotlinx.serialization through this connection's [json], so what is stored stays readable in
 * `redis-cli` and indexable by RediSearch and RedisJSON; binary payloads are the case that pays for
 * it, and they pay in base64. A value that must not be JSON goes through a `ValueCodec` instead,
 * which is what the typed layers hold underneath.
 *
 * One connection is the right number. Lettuce multiplexes every command over it and is thread-safe,
 * so a pool buys nothing until something blocks the connection — a transaction, a blocking pop, or
 * a subscription, which is why [pubSub] opens its own.
 */
class Redis internal constructor(
    private val client: RedisClient,
    private val connection: StatefulRedisConnection<String, String>,
    val namespace: String,
    /** What `cache`, `topic`, `topicPattern` and `stream` serialize through unless handed another. */
    val json: Json,
) : AutoCloseable {
    private val guard = CloseGuard()

    /** The whole of Lettuce's suspending API, for everything the typed layers do not cover. */
    val commands: RedisCoroutinesCommands<String, String> by lazy { connection.coroutines() }

    /** This connection's [namespace], and [parts] under it. */
    fun key(vararg parts: String): String = redisKey(namespace, *parts)

    suspend fun ping(): String = commands.ping() ?: error("PING answered nothing")

    /**
     * A connection of its own, for subscribing.
     *
     * A subscribed connection can only be spoken to in the subscribe protocol, so sharing
     * [connection] would take the rest of the application's commands down with it. The caller
     * closes what it opens.
     */
    fun pubSub(): StatefulRedisPubSubConnection<String, String> = client.connectPubSub()

    /**
     * A connection of this application's own, for a command that will block.
     *
     * `XREADGROUP BLOCK`, `BLPOP` and their kind hold the connection until they answer, and Lettuce
     * multiplexes every other command over the same one — so a blocking read on [commands] stalls
     * the whole application for as long as it blocks. The caller closes what it opens.
     */
    fun dedicated(): StatefulRedisConnection<String, String> = client.connect()

    /** Closes the connection and shuts the client down. Calling it again does nothing. */
    override fun close() =
        guard.once {
            connection.close()
            client.shutdown()
        }

    companion object {
        fun connect(config: RedisConfig = RedisConfig()): Redis {
            val uri = RedisURI.create(config.uri).apply { timeout = JavaDuration.ofMillis(config.timeout.inWholeMilliseconds) }
            val client = RedisClient.create(uri)
            return Redis(client, client.connect(), config.namespace, config.json)
        }
    }
}
