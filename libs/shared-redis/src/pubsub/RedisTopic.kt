package com.strange.redis.pubsub

import com.strange.redis.Redis
import com.strange.redis.codec.JsonValueCodec
import com.strange.redis.codec.ValueCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * One pub/sub channel, typed.
 *
 * ```kotlin
 * val events = redis.topic<OrderEvent>("orders")
 * events.subscribe().collect { handle(it) }
 * events.publish(OrderEvent.Placed(id))
 * ```
 *
 * **Pub/sub delivers to whoever is listening right now, and forgets.** Nothing is stored, nothing is
 * replayed, and a subscriber that was reconnecting missed whatever went past. That is the right
 * shape for a cache invalidation or a "something changed, go and look" nudge, and the wrong one for
 * anything that has to happen — for that there is `RedisStream`, which keeps the message until it is
 * acknowledged. [publish] returning 0 is the honest signal: nobody heard that.
 */
class RedisTopic<T>(
    private val redis: Redis,
    val name: String,
    private val codec: ValueCodec<T>,
) {
    /** The same topic, named by its serializer — for a call site whose `T` cannot be reified. */
    constructor(
        redis: Redis,
        name: String,
        serializer: KSerializer<T>,
        json: Json = redis.json,
    ) : this(redis, name, JsonValueCodec(json, serializer))

    val channel: String get() = redis.key("topic", name)

    /** How many subscribers the server handed it to. Zero means it is gone. */
    suspend fun publish(message: T): Long = redis.commands.publish(channel, codec.encode(message)) ?: 0L

    /**
     * Messages on this channel, until the collector stops.
     *
     * A connection of its own, opened when collection starts and closed when it ends: a subscribed
     * connection speaks only the subscribe protocol, so sharing the main one would take the rest of
     * the application's commands down with it.
     *
     * The listener is registered *before* `SUBSCRIBE` goes out, which is the whole reason this is a
     * `callbackFlow` over Lettuce's listener API rather than its reactive `observeChannels()`: with
     * the reactive flux, the window between the server accepting the subscription and Reactor
     * attaching its sink is a window where messages are dropped.
     *
     * A message this consumer cannot decode ends the flow. Skipping it would turn a version skew
     * between producer and consumer into data that quietly goes missing.
     */
    fun subscribe(): Flow<T> =
        callbackFlow {
            val connection = redis.pubSub()
            val listener =
                object : RedisPubSubAdapter<String, String>() {
                    override fun message(
                        channel: String,
                        message: String,
                    ) {
                        if (channel == this@RedisTopic.channel) {
                            runCatching { codec.decode(message) }
                                .onSuccess { trySend(it) }
                                .onFailure { close(it) }
                        }
                    }
                }

            connection.addListener(listener)
            connection.reactive().subscribe(this@RedisTopic.channel).awaitFirstOrNull()

            awaitClose {
                connection.removeListener(listener)
                connection.close()
            }
        }
}

/**
 * A topic carrying `T`, serialized with kotlinx.serialization through this connection's `Json`.
 *
 * ```kotlin
 * redis.topic<OrderEvent>("orders").subscribe().collect { handle(it) }
 * ```
 */
inline fun <reified T> Redis.topic(
    name: String,
    json: Json = this.json,
): RedisTopic<T> = RedisTopic(this, name, ValueCodec.json<T>(json))
