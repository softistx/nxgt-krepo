package com.softistx.redis.pubsub

import com.softistx.redis.Redis
import com.softistx.redis.codec.JsonValueCodec
import com.softistx.redis.codec.ValueCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Every topic matching a glob, as one flow.
 *
 * ```kotlin
 * redis.topicPattern<OrderEvent>("orders:*")
 *     .subscribe()
 *     .collect { (channel, event) -> ... }
 * ```
 *
 * Separate from [RedisTopic] because a pattern has no publish: `PUBLISH orders:*` sends to a channel
 * literally called `orders:*`, which nobody is listening to. The two look similar and behave
 * differently, so they are two classes rather than one with a mode.
 *
 * The pattern is resolved under this connection's namespace, so `orders:*` cannot reach another
 * application's topics.
 */
class RedisTopicPattern<T>(
    private val redis: Redis,
    val pattern: String,
    private val codec: ValueCodec<T>,
) {
    /** The same subscription, named by its serializer — for a call site whose `T` cannot be reified. */
    constructor(
        redis: Redis,
        pattern: String,
        serializer: KSerializer<T>,
        json: Json = redis.json,
    ) : this(redis, pattern, JsonValueCodec(json, serializer))

    val channelPattern: String get() = redis.key("topic", pattern)

    fun subscribe(): Flow<TopicMessage<T>> =
        callbackFlow {
            val connection = redis.pubSub()
            val listener =
                object : RedisPubSubAdapter<String, String>() {
                    override fun message(
                        pattern: String,
                        channel: String,
                        message: String,
                    ) {
                        runCatching { codec.decode(message) }
                            .onSuccess { trySend(TopicMessage(channel, it)) }
                            .onFailure { close(it) }
                    }
                }

            connection.addListener(listener)
            connection.reactive().psubscribe(channelPattern).awaitFirstOrNull()

            awaitClose {
                connection.removeListener(listener)
                connection.close()
            }
        }
}

/**
 * Every topic matching [pattern], carrying `T`, serialized through this connection's `Json`.
 *
 * ```kotlin
 * redis.topicPattern<OrderEvent>("orders:*").subscribe().collect { (channel, event) -> ... }
 * ```
 */
inline fun <reified T> Redis.topicPattern(
    pattern: String,
    json: Json = this.json,
): RedisTopicPattern<T> = RedisTopicPattern(this, pattern, ValueCodec.json<T>(json))
