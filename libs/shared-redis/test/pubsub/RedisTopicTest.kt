package com.strange.redis.pubsub

import com.strange.redis.RedisTestServer
import com.strange.redis.codec.ValueCodec
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
 * designed, and also the trap in testing it. Every scenario here publishes until the server says a
 * subscriber was there, which is what `publish`'s return value is for.
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
                    val orders = RedisTopic(redis, "orders", ValueCodec.string)

                    coroutineScope {
                        val one = async { orders.subscribe().take(3).toList() }
                        val two = async { orders.subscribe().take(3).toList() }

                        // Wait for both, not just the first — two subscribers, count of two.
                        while (orders.publish("warmup") < 2L) delay(20)

                        listOf("a", "b").forEach { orders.publish(it) }

                        one.await() shouldBe listOf("warmup", "a", "b")
                        two.await() shouldBe listOf("warmup", "a", "b")
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
