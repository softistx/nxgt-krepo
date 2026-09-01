package com.softistx.amqp.retry

import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpTestBroker
import com.softistx.amqp.consumer.consumer
import com.softistx.amqp.publisher.publisher
import com.softistx.amqp.topology.Topology
import com.softistx.amqp.topology.declare
import com.softistx.amqp.topology.delete
import com.softistx.amqp.topology.messageCount
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The retry path end to end, against a real broker with the delays turned down to milliseconds.
 *
 * There is nothing to fake here: the mechanism *is* the broker expiring a message in a queue nobody
 * consumes and dead-lettering it back. A unit test of it would be a unit test of a TTL.
 */
class RetryQueueTest :
    FeatureSpec({

        @Serializable
        data class Order(
            val id: String,
        )

        suspend fun <T> withWorkQueue(block: suspend (Amqp, String) -> T): T =
            AmqpTestBroker.amqp { amqp ->
                val queue = AmqpTestBroker.name("work")
                val topology: Topology = amqp.declare { queue(queue) }
                try {
                    block(amqp, queue)
                } finally {
                    amqp.delete(topology)
                }
            }

        suspend fun Amqp.publish(
            queue: String,
            order: Order,
        ) = publisher<Order>().use { it.publish(order, routingKey = queue) }

        feature("a message that fails").config(enabled = AmqpTestBroker.available) {
            scenario("comes back after the delay, and says which attempt it is on") {
                withWorkQueue { amqp, queue ->
                    val retries = amqp.retryQueue<Order>(queue, RetryPolicy(listOf(200.milliseconds, 200.milliseconds)))
                    val attempts = ConcurrentLinkedQueue<Int>()

                    try {
                        amqp.publish(queue, Order("A1"))

                        coroutineScope {
                            val consuming =
                                launch {
                                    amqp.consumer<Order>(queue).use { consumer ->
                                        consumer.processWithRetry(retries) { message ->
                                            attempts += message.headers.int(RetryQueue.ATTEMPT) ?: 0
                                            // Fails once; the delivery after the delay succeeds.
                                            if (attempts.size == 1) error("not this time")
                                        }
                                    }
                                }

                            withTimeout(10.seconds) { while (attempts.size < 2) delay(20) }
                            consuming.cancel()
                        }

                        // First delivery carries no attempt header; the copy carries 1.
                        attempts.toList() shouldContainExactly listOf(0, 1)
                        amqp.messageCount(retries.parked) shouldBe 0L
                    } finally {
                        retries.close()
                        amqp.delete(retries)
                    }
                }
            }

            scenario("is parked once its attempts are used up, with why and where from") {
                withWorkQueue { amqp, queue ->
                    val retries = amqp.retryQueue<Order>(queue, RetryPolicy(listOf(150.milliseconds)))
                    val seen = ConcurrentLinkedQueue<Int>()

                    try {
                        amqp.publish(queue, Order("A1"))

                        coroutineScope {
                            val consuming =
                                launch {
                                    amqp.consumer<Order>(queue).use { consumer ->
                                        consumer.processWithRetry(retries) { message ->
                                            seen += message.headers.int(RetryQueue.ATTEMPT) ?: 0
                                            throw IllegalStateException("always fails")
                                        }
                                    }
                                }

                            // One delay, so: first delivery, one retry, then parked.
                            withTimeout(10.seconds) { while (amqp.messageCount(retries.parked) == 0L) delay(20) }
                            /* Only *then* is the copy's original acknowledged. Cancelling in between
                               closes the channel on an unacknowledged delivery, which the broker
                               rightly puts back on the queue — at-least-once working as designed,
                               and a race if the count below is read without waiting for it. */
                            withTimeout(10.seconds) { while (amqp.messageCount(queue) != 0L) delay(20) }
                            consuming.cancel()
                        }

                        seen.toList() shouldContainExactly listOf(0, 1)
                        amqp.messageCount(queue) shouldBe 0L

                        val parked = amqp.withChannel { channel -> channel.basicGet(retries.parked, true) }
                        parked.props.headers[RetryQueue.REASON].toString() shouldBe "java.lang.IllegalStateException"
                        parked.props.headers[RetryQueue.ORIGIN].toString() shouldBe queue
                        parked.props.headers[RetryQueue.ATTEMPT] shouldBe 2
                    } finally {
                        retries.close()
                        amqp.delete(retries)
                    }
                }
            }

            scenario("a message that is handled is acknowledged and never enters the retry path") {
                withWorkQueue { amqp, queue ->
                    val retries = amqp.retryQueue<Order>(queue, RetryPolicy(listOf(200.milliseconds)))
                    val handled = ConcurrentLinkedQueue<String>()

                    try {
                        amqp.publish(queue, Order("A1"))

                        coroutineScope {
                            val consuming =
                                launch {
                                    amqp.consumer<Order>(queue).use { consumer ->
                                        consumer.processWithRetry(retries) { handled += it.body.id }
                                    }
                                }

                            withTimeout(10.seconds) { while (handled.isEmpty()) delay(20) }
                            consuming.cancel()
                        }

                        amqp.messageCount(queue) shouldBe 0L
                        amqp.messageCount(retries.delayQueues.single()) shouldBe 0L
                        amqp.messageCount(retries.parked) shouldBe 0L
                    } finally {
                        retries.close()
                        amqp.delete(retries)
                    }
                }
            }
        }
    })
