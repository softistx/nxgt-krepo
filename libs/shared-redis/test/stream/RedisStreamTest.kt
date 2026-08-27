package com.strange.redis.stream

import com.strange.redis.RedisTestServer
import com.strange.redis.codec.ValueCodec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class OrderEvent(
    val id: String,
    val status: String = "PLACED",
)

private const val POLL_MS = 200L

class RedisStreamTest :
    FeatureSpec({

        feature("appending").config(enabled = RedisTestServer.available) {
            scenario("entries keep their order and their ids") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())

                    val first = orders.append(OrderEvent("o1"))
                    orders.append(OrderEvent("o2"))

                    orders.length() shouldBe 2
                    orders.history().map { it.value.id } shouldBe listOf("o1", "o2")
                    orders.history().first().id shouldBe first
                }
            }

            scenario("an exact trim holds the bound") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())
                    repeat(10) { orders.append(OrderEvent("o$it")) }

                    orders.trim(4, approximate = false) shouldBe 6
                    orders.length() shouldBe 4
                    orders.history().map { it.value.id } shouldBe listOf("o6", "o7", "o8", "o9")
                }
            }

            scenario("a capped stream stops growing") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>(), maxLength = 100)
                    repeat(500) { orders.append(OrderEvent("o$it")) }

                    /* Approximate trimming drops whole macro-nodes, so the length lands near the cap
                       rather than on it — the guarantee is that 500 appends are not 500 entries. */
                    orders.length() shouldBeGreaterThanOrEqual 100
                    orders.length() shouldBeLessThanOrEqual 200
                }
            }
        }

        feature("a consumer group").config(enabled = RedisTestServer.available) {
            scenario("creating it twice is not an error") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())

                    orders.createGroup("billing")
                    orders.createGroup("billing")

                    orders.pending("billing") shouldBe 0
                }
            }

            scenario("it starts at the end — a group is a subscription, not a backfill") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())
                    orders.append(OrderEvent("before"))
                    orders.createGroup("billing")
                    orders.append(OrderEvent("after"))

                    val seen =
                        withTimeout(10.seconds) {
                            orders.consume("billing", "worker-1", block = POLL_MS.milliseconds).take(1).toList()
                        }

                    seen.map { it.value.id } shouldBe listOf("after")
                }
            }

            scenario("two consumers in one group split the entries between them") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())
                    orders.createGroup("billing")
                    repeat(6) { orders.append(OrderEvent("o$it")) }

                    val seen =
                        withTimeout(20.seconds) {
                            coroutineScope {
                                val one =
                                    async {
                                        orders
                                            .consume("billing", "worker-1", block = POLL_MS.milliseconds, count = 1)
                                            .take(3)
                                            .toList()
                                    }
                                val two =
                                    async {
                                        orders
                                            .consume("billing", "worker-2", block = POLL_MS.milliseconds, count = 1)
                                            .take(3)
                                            .toList()
                                    }
                                (one.await() + two.await()).map { it.value.id }
                            }
                        }

                    seen shouldContainExactlyInAnyOrder listOf("o0", "o1", "o2", "o3", "o4", "o5")
                }
            }
        }

        feature("acknowledgement").config(enabled = RedisTestServer.available) {
            scenario("an entry stays pending until it is acknowledged") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())
                    orders.createGroup("billing")
                    orders.append(OrderEvent("o1"))

                    val record =
                        withTimeout(10.seconds) {
                            orders.consume("billing", "worker-1", block = POLL_MS.milliseconds).take(1).toList()
                        }.single()

                    orders.pending("billing") shouldBe 1
                    orders.ack("billing", record.id) shouldBe 1
                    orders.pending("billing") shouldBe 0
                }
            }

            scenario("process acknowledges what its handler returned from") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())
                    orders.createGroup("billing")
                    orders.append(OrderEvent("o1"))

                    val handled = mutableListOf<String>()
                    shouldThrow<kotlinx.coroutines.TimeoutCancellationException> {
                        withTimeout(2.seconds) {
                            orders.process("billing", "worker-1", block = POLL_MS.milliseconds) { event ->
                                handled += event.id
                            }
                        }
                    }

                    handled shouldBe listOf("o1")
                    orders.pending("billing") shouldBe 0
                }
            }

            scenario("a handler that throws leaves the entry for somebody else to claim") {
                RedisTestServer.withRedis { redis ->
                    val orders = RedisStream(redis, "orders", ValueCodec.json<OrderEvent>())
                    orders.createGroup("billing")
                    orders.append(OrderEvent("o1"))

                    shouldThrow<IllegalStateException> {
                        withTimeout(10.seconds) {
                            orders.process("billing", "worker-1", block = POLL_MS.milliseconds) { error("no") }
                        }
                    }

                    orders.pending("billing") shouldBe 1

                    val claimed = orders.claimStale("billing", "worker-2", minIdle = 0.milliseconds)
                    claimed.map { it.value.id } shouldBe listOf("o1")

                    orders.ack("billing", claimed.single().id) shouldBe 1
                    orders.pending("billing") shouldBe 0
                }
            }
        }
    })
