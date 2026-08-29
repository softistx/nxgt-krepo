package com.strange.amqp.publisher

import com.strange.amqp.Amqp
import com.strange.amqp.AmqpTestBroker
import com.strange.amqp.AmqpUnroutableException
import com.strange.amqp.message.headersOf
import com.strange.amqp.topology.Topology
import com.strange.amqp.topology.declare
import com.strange.amqp.topology.delete
import com.strange.amqp.topology.messageCount
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

/**
 * Publishing against a real broker, because the interesting answers are all the broker's.
 *
 * Two of these are here to pin down behaviour that looks like success and is not: a message routed
 * nowhere, and a publish that returns before the broker has the message. Both are silent by
 * default in AMQP, and both are what a publisher wrapper is for.
 */
class AmqpPublisherTest :
    FeatureSpec({

        @Serializable
        data class Order(
            val id: String,
        )

        suspend fun <T> withTopology(block: suspend (Amqp, String, String) -> T): T =
            AmqpTestBroker.amqp { amqp ->
                val exchange = AmqpTestBroker.name("orders")
                val queue = AmqpTestBroker.name("billing")
                val topology: Topology =
                    amqp.declare {
                        exchange(exchange)
                        queue(queue) { bindTo(exchange, "order.placed") }
                    }
                try {
                    block(amqp, exchange, queue)
                } finally {
                    amqp.delete(topology)
                }
            }

        suspend fun Amqp.eventualCount(
            queue: String,
            expected: Long,
        ) = withTimeout(5.seconds) {
            while (messageCount(queue) != expected) delay(20)
            expected
        }

        feature("publishing").config(enabled = AmqpTestBroker.available) {
            scenario("the call returns once the broker has confirmed the message") {
                withTopology { amqp, exchange, queue ->
                    amqp.publisher<Order>(exchange).use { orders ->
                        val published = orders.publish(Order("A1"), routingKey = "order.placed")

                        published.exchange shouldBe exchange
                        published.routingKey shouldBe "order.placed"
                        /* The sequence number is the broker's own handle for the message, and its
                           presence is what says a confirm actually came back. */
                        published.sequence shouldBeGreaterThan 0L
                    }

                    amqp.eventualCount(queue, 1)
                }
            }

            scenario("the body and its headers arrive as they were sent") {
                withTopology { amqp, exchange, queue ->
                    amqp.publisher<Order>(exchange).use { orders ->
                        orders.publish(
                            Order("A1"),
                            routingKey = "order.placed",
                            headers = headersOf("tenant" to "acme"),
                            messageId = "m-1",
                        )
                    }

                    amqp.eventualCount(queue, 1)
                    val delivered = amqp.withChannel { channel -> channel.basicGet(queue, true) }

                    delivered.body.decodeToString() shouldBe """{"id":"A1"}"""
                    delivered.props.contentType shouldBe "application/json"
                    delivered.props.messageId shouldBe "m-1"
                    delivered.props.headers["tenant"].toString() shouldBe "acme"
                    // Persistent by default: the half of durability the publisher owns.
                    delivered.props.deliveryMode shouldBe 2
                }
            }

            scenario("a batch keeps the order it was written in") {
                withTopology { amqp, exchange, queue ->
                    amqp.publisher<Order>(exchange).use { orders ->
                        orders.publishAll((1..20).map { Order("A$it") }, routingKey = "order.placed")
                    }

                    amqp.eventualCount(queue, 20)
                    val read =
                        amqp.withChannel { channel ->
                            (1..20).map { channel.basicGet(queue, true).body.decodeToString() }
                        }

                    read shouldBe (1..20).map { """{"id":"A$it"}""" }
                }
            }
        }

        feature("a message nothing is bound for").config(enabled = AmqpTestBroker.available) {
            scenario("without mandatory it is discarded and reported as success") {
                /* This is the trap, pinned down on purpose: AMQP's default is that a message routed
                   nowhere is a message delivered. Nothing throws, and the queue stays empty. */
                withTopology { amqp, exchange, queue ->
                    amqp.publisher<Order>(exchange).use { orders ->
                        orders.publish(Order("A1"), routingKey = "nobody.listens")
                    }

                    amqp.messageCount(queue) shouldBe 0L
                }
            }

            scenario("with mandatory it fails, naming the exchange and the routing key") {
                withTopology { amqp, exchange, _ ->
                    amqp.publisher<Order>(exchange, PublisherOptions(mandatory = true)).use { orders ->
                        val failure =
                            shouldThrow<AmqpUnroutableException> {
                                orders.publish(Order("A1"), routingKey = "nobody.listens")
                            }

                        failure.exchange shouldBe exchange
                        failure.routingKey shouldBe "nobody.listens"
                    }
                }
            }

            scenario("a returned message does not take the publishes around it with it") {
                withTopology { amqp, exchange, queue ->
                    amqp.publisher<Order>(exchange, PublisherOptions(mandatory = true)).use { orders ->
                        orders.publish(Order("A1"), routingKey = "order.placed")
                        shouldThrow<AmqpUnroutableException> { orders.publish(Order("A2"), routingKey = "nobody.listens") }
                        orders.publish(Order("A3"), routingKey = "order.placed")
                    }

                    amqp.eventualCount(queue, 2)
                }
            }
        }

        feature("a publisher that has been closed").config(enabled = AmqpTestBroker.available) {
            scenario("publishing on it fails rather than waiting for a confirm nobody will send") {
                /* The mailbox is closed with the channel, so the settler drains what it has and
                   fails the rest. A caller awaiting an answer that is not coming is the one
                   outcome worse than an error. */
                withTopology { amqp, exchange, _ ->
                    val orders = amqp.publisher<Order>(exchange)
                    orders.close()

                    shouldThrow<Throwable> { orders.publish(Order("A1"), routingKey = "order.placed") }
                }
            }
        }

        feature("publishing straight to a queue").config(enabled = AmqpTestBroker.available) {
            scenario("the default exchange routes by queue name") {
                withTopology { amqp, _, queue ->
                    amqp.publisher<Order>().use { direct -> direct.publish(Order("A1"), routingKey = queue) }

                    amqp.eventualCount(queue, 1)
                }
            }
        }
    })
