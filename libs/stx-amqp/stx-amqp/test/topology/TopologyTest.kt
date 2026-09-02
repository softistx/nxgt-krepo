package com.softistx.amqp.topology

import com.softistx.amqp.AmqpTestBroker
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/**
 * Declaring against a real broker, which is the only place the interesting answers are.
 *
 * A declaration that is wrong does not fail in Kotlin — it fails in the broker, on a channel that
 * then closes. So what is worth proving here is that a whole topology goes up in one call whatever
 * order it was written in, that declaring it twice is success, and that the guards on delete do
 * what they say.
 */
class TopologyTest :
    FeatureSpec({

        feature("declaring a topology").config(enabled = AmqpTestBroker.available) {
            scenario("exchanges, queues and bindings go up in one call, in the order the broker needs") {
                AmqpTestBroker.amqp { amqp ->
                    val exchange = AmqpTestBroker.name("orders")
                    val queue = AmqpTestBroker.name("billing")

                    /* Deliberately written queue-first: the queue's binding names an exchange that
                       has not been mentioned yet, which is the ordering a caller should not have to
                       think about. */
                    val topology =
                        amqp.declare {
                            queue(queue) { bindTo(exchange, "order.placed") }
                            exchange(exchange)
                        }

                    try {
                        amqp.queueExists(queue) shouldBe true
                        amqp.exchangeExists(exchange) shouldBe true
                        topology.bindings.single().routingKey shouldBe "order.placed"
                    } finally {
                        amqp.delete(topology)
                    }
                }
            }

            scenario("declaring the same thing again is success, which is what a restart does") {
                AmqpTestBroker.amqp { amqp ->
                    val queue = AmqpTestBroker.name("idempotent")
                    val declare: suspend () -> Topology = { amqp.declare { queue(queue) { messageTtl = 30.seconds } } }

                    val topology = declare()
                    try {
                        declare()
                        amqp.queueExists(queue) shouldBe true
                    } finally {
                        amqp.delete(topology)
                    }
                }
            }

            scenario("a queue nobody declared does not exist, and asking does not break the connection") {
                /* A failed passive declare closes its channel; if that channel were shared, this
                   question would take the caller's publisher down with it. */
                AmqpTestBroker.amqp { amqp ->
                    amqp.queueExists(AmqpTestBroker.name("absent")) shouldBe false
                    amqp.isOpen shouldBe true
                    amqp.queueExists(AmqpTestBroker.name("absent-again")) shouldBe false
                }
            }
        }

        feature("what is in a queue").config(enabled = AmqpTestBroker.available) {
            scenario("its depth and its consumer count are readable without reading it") {
                AmqpTestBroker.amqp { amqp ->
                    val queue = AmqpTestBroker.name("depth")
                    val topology = amqp.declare { queue(queue) }

                    try {
                        amqp.messageCount(queue) shouldBe 0L
                        amqp.consumerCount(queue) shouldBe 0L

                        amqp.withChannel { channel -> channel.basicPublish("", queue, null, "one".toByteArray()) }
                        eventually { amqp.messageCount(queue) == 1L }

                        amqp.purgeQueue(queue) shouldBe 1
                        amqp.messageCount(queue) shouldBe 0L
                    } finally {
                        amqp.delete(topology)
                    }
                }
            }
        }
    })

private suspend fun eventually(condition: suspend () -> Boolean) {
    kotlinx.coroutines.withTimeout(5.seconds) {
        while (!condition()) kotlinx.coroutines.delay(20)
    }
}
