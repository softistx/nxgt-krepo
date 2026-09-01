package com.softistx.redis.pubsub

import com.softistx.redis.RedisTestServer
import com.softistx.redis.codec.ValueCodec
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable

@Serializable
private data class OrderEvent(
    val id: String,
    val status: String,
)

/**
 * Publishing before anyone is listening is a message nobody gets — which is pub/sub working as
 * designed, and also the trap in testing it. A scenario with one subscriber publishes until the
 * server says somebody heard it, which is what `publish`'s return value is for.
 *
 * **That probe does not generalise to two subscribers.** `publish` returning 1 of an expected 2 is
 * not a failed probe — it already delivered to the one that was ready, so looping leaves the faster
 * subscriber a message ahead of the slower one. `PUBSUB NUMSUB` asks how many are registered without
 * sending anything, which is the probe that works for any number of them.
 */
class RedisTopicTest :
    FeatureSpec({

        feature("publishing to a subscriber").config(enabled = RedisTestServer.available) {
            scenario("what was published is what arrives, typed") {
                RedisTestServer.withRedis { redis ->
                    val orders = redis.topic<OrderEvent>("orders")

                    coroutineScope {
                        val received = async { orders.subscribe().first() }
                        val event = OrderEvent("o1", "PLACED")

                        while (orders.publish(event) == 0L) delay(20)

                        received.await() shouldBe event
                    }
                }
            }

            scenario("every message goes to every subscriber, in order") {
                RedisTestServer.withRedis { redis ->
                    // A channel of its own, so the count below is this scenario's subscribers and
                    // not a connection another scenario has not finished closing.
                    val orders = RedisTopic(redis, "orders-fanout", ValueCodec.string)

                    suspend fun subscribers(): Long = redis.commands.pubsubNumsub(orders.channel)[orders.channel] ?: 0L

                    coroutineScope {
                        val one = async { orders.subscribe().take(3).toList() }
                        val two = async { orders.subscribe().take(3).toList() }

                        // Wait for both to be registered, without publishing to do it.
                        while (subscribers() < 2L) delay(20)

                        listOf("a", "b", "c").forEach { orders.publish(it) }

                        one.await() shouldBe listOf("a", "b", "c")
                        two.await() shouldBe listOf("a", "b", "c")
                    }
                }
            }
        }

        feature("publishing to nobody").config(enabled = RedisTestServer.available) {
            scenario("it says so, and the message is gone") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisTopic(redis, "orders", ValueCodec.string)

                    orders.publish("into the void") shouldBe 0L

                    coroutineScope {
                        val received = async { orders.subscribe().first() }
                        while (orders.publish("heard") == 0L) delay(20)

                        // The first message is not replayed to a subscriber that arrived later.
                        received.await() shouldBe "heard"
                    }
                }
            }
        }

        feature("a pattern subscription").config(enabled = RedisTestServer.available) {
            scenario("it hears every matching topic and says which one it was") {
                RedisTestServer.withRedis { redis ->
                    val placed = RedisTopic(redis, "orders:placed", ValueCodec.string)
                    val shipped = RedisTopic(redis, "orders:shipped", ValueCodec.string)
                    val all = RedisTopicPattern(redis, "orders:*", ValueCodec.string)

                    coroutineScope {
                        val received = async { all.subscribe().take(2).toList() }
                        while (placed.publish("o1") == 0L) delay(20)
                        shipped.publish("o2")

                        received.await() shouldBe
                            listOf(
                                TopicMessage(placed.channel, "o1"),
                                TopicMessage(shipped.channel, "o2"),
                            )
                    }
                }
            }

            scenario("the pattern cannot reach past the namespace") {
                RedisTestServer.withRedis { redis ->
                    RedisTopicPattern(redis, "orders:*", ValueCodec.string).channelPattern shouldBe
                        "${redis.namespace}:topic:orders:*"
                }
            }
        }
    })
