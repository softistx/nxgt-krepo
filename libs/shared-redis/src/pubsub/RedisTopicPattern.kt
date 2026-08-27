package com.strange.redis.pubsub

import com.strange.redis.Redis
import com.strange.redis.codec.ValueCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull

/**
 * Every topic matching a glob, as one flow.
 *
 * ```kotlin
 * RedisTopicPattern(redis, "orders:*", ValueCodec.json<OrderEvent>())
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
