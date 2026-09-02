package com.softistx.amqp.topology

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

/**
 * The arguments a queue declaration turns into.
 *
 * These are the `x-` names the broker actually reads, and getting one wrong is not a compile error
 * or even a declare failure — it is a queue that quietly has no dead-letter exchange and drops what
 * it rejects. Worth a spec that needs no broker.
 */
class DeclarationsTest :
    FeatureSpec({

        feature("what a queue declares") {
            scenario("a plain classic queue passes no arguments at all") {
                Queue("billing").asArguments() shouldBe emptyMap()
            }

            scenario("a dead-letter exchange, and a routing key only when one was asked for") {
                Queue("billing", deadLetter = DeadLetter("orders.dead")).asArguments() shouldBe
                    mapOf("x-dead-letter-exchange" to "orders.dead")

                Queue("billing", deadLetter = DeadLetter("orders.dead", "retry")).asArguments() shouldBe
                    mapOf("x-dead-letter-exchange" to "orders.dead", "x-dead-letter-routing-key" to "retry")
            }

            scenario("a TTL is milliseconds, whatever unit it was written in") {
                Queue("delay", messageTtl = 5.minutes).asArguments() shouldBe mapOf("x-message-ttl" to 300_000L)
            }

            scenario("a quorum queue says so, and a classic one does not") {
                Queue("billing", type = QueueType.Quorum).asArguments() shouldBe mapOf("x-queue-type" to "quorum")
                Queue("billing", type = QueueType.Classic).asArguments() shouldNotContainKey "x-queue-type"
            }

            scenario("the caller's own arguments win, so nothing here is a ceiling") {
                val queue = Queue("billing", maxLength = 10, arguments = mapOf("x-max-length" to 99L, "x-overflow" to "reject-publish"))

                queue.asArguments() shouldBe mapOf("x-max-length" to 99L, "x-overflow" to "reject-publish")
            }
        }

        feature("declarations the broker would reject") {
            scenario("a quorum queue that is exclusive is refused here rather than there") {
                /* The broker's own answer is a channel-closing error mid-deploy; this one is a
                   message naming the queue. */
                shouldThrow<IllegalArgumentException> { Queue("billing", type = QueueType.Quorum, exclusive = true) }
                shouldThrow<IllegalArgumentException> { Queue("billing", type = QueueType.Quorum, durable = false) }
            }

            scenario("a TTL or a length that is not positive") {
                shouldThrow<IllegalArgumentException> { Queue("billing", messageTtl = (-1).minutes) }
                shouldThrow<IllegalArgumentException> { Queue("billing", maxLength = 0) }
            }
        }

        feature("the declaration block") {
            scenario("bindings written inside a queue belong to that queue") {
                val topology =
                    TopologyScope()
                        .apply {
                            exchange("orders")
                            queue("billing") {
                                deadLetterTo("orders.dead")
                                bindTo("orders", "order.placed", "order.cancelled")
                            }
                        }.build()

                topology.exchanges.map { it.name } shouldBe listOf("orders")
                topology.queues.single().deadLetter shouldBe DeadLetter("orders.dead")
                topology.bindings shouldBe
                    listOf(
                        Binding("billing", "orders", "order.placed"),
                        Binding("billing", "orders", "order.cancelled"),
                    )
            }

            scenario("binding to a fanout exchange needs no routing key") {
                val topology = TopologyScope().apply { queue("audit") { bindTo("events") } }.build()

                topology.bindings shouldBe listOf(Binding("audit", "events", ""))
            }
        }
    })
