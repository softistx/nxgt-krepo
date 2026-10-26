package com.softistx.amqp.consumer

import com.rabbitmq.client.AlreadyClosedException
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ShutdownSignalException
import com.softistx.amqp.Amqp
import com.softistx.amqp.AmqpTestBroker
import com.softistx.amqp.AmqpValueException
import com.softistx.amqp.codec.AmqpCodec
import com.softistx.amqp.publisher.publisher
import com.softistx.amqp.topology.ExchangeType
import com.softistx.amqp.topology.Topology
import com.softistx.amqp.topology.declare
import com.softistx.amqp.topology.delete
import com.softistx.amqp.topology.messageCount
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Consuming against a real broker.
 *
 * There is no fake worth writing here: what is being tested is the broker's own behaviour —
 * that an acknowledgement removes a message and a rejection moves it, that an unacknowledged
 * message comes back, and that prefetch is what decides how many a consumer may hold. A mock of
 * that would be a mock of the thing under test.
 */
class AmqpConsumerTest :
    FeatureSpec({

        @Serializable
        data class Order(
            val id: String,
        )

        /** A queue, its dead-letter queue, and everything cleaned up afterwards. */
        suspend fun <T> withQueues(block: suspend (Amqp, String, String) -> T): T =
            AmqpTestBroker.amqp { amqp ->
                val dead = AmqpTestBroker.name("dead")
                val deadExchange = "$dead.exchange"
                val queue = AmqpTestBroker.name("work")
                val topology: Topology =
                    amqp.declare {
                        exchange(deadExchange, ExchangeType.Fanout)
                        queue(dead) { bindTo(deadExchange) }
                        queue(queue) { deadLetterTo(deadExchange) }
                    }
                try {
                    block(amqp, queue, dead)
                } finally {
                    amqp.delete(topology)
                }
            }

        suspend fun Amqp.publish(
            queue: String,
            vararg orders: Order,
        ) = publisher<Order>().use { publisher -> orders.forEach { publisher.publish(it, routingKey = queue) } }

        suspend fun Amqp.eventually(
            queue: String,
            count: Long,
        ) = withTimeout(10.seconds) {
            while (messageCount(queue) != count) delay(20)
        }

        feature("reading messages").config(enabled = AmqpTestBroker.available) {
            scenario("they arrive as a flow, in order, with where they came from") {
                withQueues { amqp, queue, _ ->
                    amqp.publish(queue, Order("A1"), Order("A2"), Order("A3"))

                    val messages =
                        amqp.consumer<Order>(queue).use { consumer ->
                            withTimeout(10.seconds) { consumer.messages().take(3).toList() }
                        }

                    messages.map { it.body.id } shouldContainExactly listOf("A1", "A2", "A3")
                    messages.first().routingKey shouldBe queue
                    messages.first().redelivered shouldBe false
                }
            }

            scenario("a message the collector never acknowledged goes back to the queue") {
                /* The reason ack-after-handler is safe: giving up on a message is not the same as
                   consuming it, and the broker knows the difference. */
                withQueues { amqp, queue, _ ->
                    amqp.publish(queue, Order("A1"))

                    amqp.consumer<Order>(queue).use { consumer ->
                        withTimeout(10.seconds) { consumer.messages().take(1).toList() }
                    }

                    amqp.eventually(queue, 1)
                }
            }
        }

        feature("handling messages").config(enabled = AmqpTestBroker.available) {
            scenario("a handled message is acknowledged, and the queue empties") {
                withQueues { amqp, queue, dead ->
                    amqp.publish(queue, Order("A1"), Order("A2"))
                    val handled = ConcurrentLinkedQueue<String>()

                    coroutineScope {
                        val consuming =
                            launch {
                                amqp.consumer<Order>(queue).use { it.process { message -> handled += message.body.id } }
                            }

                        withTimeout(10.seconds) { while (handled.size < 2) delay(20) }
                        consuming.cancel()
                    }

                    amqp.eventually(queue, 0)
                    amqp.messageCount(dead) shouldBe 0L
                }
            }

            scenario("a handler that throws sends its message to the dead-letter queue and carries on") {
                withQueues { amqp, queue, dead ->
                    amqp.publish(queue, Order("poison"), Order("A2"))
                    val handled = ConcurrentLinkedQueue<String>()
                    val failures = ConcurrentLinkedQueue<String>()

                    coroutineScope {
                        val consuming =
                            launch {
                                amqp.consumer<Order>(queue).use { consumer ->
                                    consumer.process(onFailure = { message, _ -> failures += message.body.id }) { message ->
                                        if (message.body.id == "poison") error("cannot handle this one")
                                        handled += message.body.id
                                    }
                                }
                            }

                        withTimeout(10.seconds) { while (handled.isEmpty()) delay(20) }
                        consuming.cancel()
                    }

                    /* The one behind the poison message was still handled — the whole point of not
                       requeueing a failure. */
                    handled.toList() shouldContainExactly listOf("A2")
                    failures.toList() shouldContainExactly listOf("poison")
                    amqp.eventually(dead, 1)
                }
            }

            scenario("a body this consumer cannot read is dead-lettered rather than stopping the queue") {
                withQueues { amqp, queue, dead ->
                    /* Published as something else entirely: the shape a rolling deploy or a foreign
                       publisher produces. */
                    amqp
                        .publisher<String>(codec = AmqpCodec.text)
                        .use { it.publish("not json at all", routingKey = queue) }
                    amqp.publish(queue, Order("A2"))

                    val handled = ConcurrentLinkedQueue<String>()
                    coroutineScope {
                        val consuming =
                            launch { amqp.consumer<Order>(queue).use { it.process { m -> handled += m.body.id } } }

                        withTimeout(10.seconds) { while (handled.isEmpty()) delay(20) }
                        consuming.cancel()
                    }

                    handled.toList() shouldContainExactly listOf("A2")
                    amqp.eventually(dead, 1)
                }
            }
        }

        feature("a consumer whose channel the broker closed").config(enabled = AmqpTestBroker.available) {
            scenario("the flow ends with the broker's reason instead of hanging") {
                withQueues { amqp, queue, _ ->
                    val channel = amqp.openChannel()
                    val consumer = AmqpConsumer(channel, queue, AmqpCodec.text, ConsumerOptions())
                    val failure =
                        async {
                            runCatching { consumer.messages().collect { } }.exceptionOrNull()
                        }
                    delay(500)
                    // A passive declare of a queue that is not there: the broker answers 404 and closes the channel.
                    runCatching { channel.queueDeclarePassive("test.no.such.queue.${AmqpTestBroker.name("x")}") }

                    withTimeout(10.seconds) { failure.await() } shouldNotBe null
                }
            }
        }

        feature("what a shutdown means for the flow") {
            fun signal(
                app: Boolean,
                reference: Any,
            ) = ShutdownSignalException(false, app, null, reference)

            val connection = Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, _, _ -> null }
            val channel = Proxy.newProxyInstance(Channel::class.java.classLoader, arrayOf(Channel::class.java)) { _, _, _ -> null }

            scenario("a connection lost under recovery leaves the flow open") {
                shutdownOutcome(signal(false, connection), recoverable = true) shouldBe ShutdownOutcome.Continue
            }
            scenario("a connection lost without recovery fails the flow") {
                shutdownOutcome(signal(false, connection), recoverable = false) shouldBe ShutdownOutcome.Fail
            }
            scenario("a channel the broker closed fails the flow even under recovery") {
                shutdownOutcome(signal(false, channel), recoverable = true) shouldBe ShutdownOutcome.Fail
            }
            scenario("a close this side asked for ends the flow quietly") {
                shutdownOutcome(signal(true, connection), recoverable = true) shouldBe ShutdownOutcome.End
            }
        }

        feature("an acknowledgement that meets a shutdown") {
            scenario("is told apart from any other failure") {
                isShutdown(AlreadyClosedException(ShutdownSignalException(false, false, null, null))) shouldBe true
                isShutdown(ShutdownSignalException(true, false, null, null)) shouldBe true
                isShutdown(IOException("write failed", ShutdownSignalException(false, false, null, null))) shouldBe true
                isShutdown(IOException("disk")) shouldBe false
                isShutdown(IllegalStateException("bug")) shouldBe false
            }
        }

        feature("a concurrency limit").config(enabled = AmqpTestBroker.available) {
            scenario("never has more handlers running than it allows") {
                withQueues { amqp, queue, _ ->
                    amqp.publish(queue, *(1..6).map { Order("A$it") }.toTypedArray())
                    val running = AtomicInteger()
                    val most = AtomicInteger()
                    val done = AtomicInteger()

                    coroutineScope {
                        val consuming =
                            launch {
                                amqp.consumer<Order>(queue, ConsumerOptions(concurrency = 2)).use { consumer ->
                                    consumer.process {
                                        most.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                                        delay(100)
                                        running.decrementAndGet()
                                        done.incrementAndGet()
                                    }
                                }
                            }
                        withTimeout(10.seconds) { while (done.get() < 6) delay(20) }
                        consuming.cancel()
                    }

                    most.get() shouldBe 2
                }
            }
        }

        feature("a body that will not decode, with Undecodable.Fail").config(enabled = AmqpTestBroker.available) {
            scenario("the message goes back on the queue and the consumer stops with the decode failure") {
                withQueues { amqp, queue, _ ->
                    amqp.publisher<String>(codec = AmqpCodec.text).use { it.publish("not json at all", routingKey = queue) }

                    val failure =
                        withTimeout(10.seconds) {
                            runCatching {
                                amqp
                                    .consumer<Order>(queue, ConsumerOptions(undecodable = Undecodable.Fail))
                                    .use { it.process { } }
                            }.exceptionOrNull()
                        }

                    (failure is AmqpValueException) shouldBe true
                    amqp.eventually(queue, 1)
                }
            }
        }

        feature("handling more than one at a time").config(enabled = AmqpTestBroker.available) {
            scenario("they run alongside each other, up to the concurrency asked for") {
                withQueues { amqp, queue, _ ->
                    amqp.publish(queue, *(1..4).map { Order("A$it") }.toTypedArray())
                    val handled = ConcurrentLinkedQueue<String>()
                    val started = TimeSource.Monotonic.markNow()

                    coroutineScope {
                        val consuming =
                            launch {
                                amqp.consumer<Order>(queue, ConsumerOptions(concurrency = 4)).use { consumer ->
                                    consumer.process { message ->
                                        delay(200)
                                        handled += message.body.id
                                    }
                                }
                            }

                        withTimeout(10.seconds) { while (handled.size < 4) delay(20) }
                        consuming.cancel()
                    }

                    // Four messages, 200ms each: one at a time is 800ms.
                    started.elapsedNow().inWholeMilliseconds shouldBeLessThan 600L
                    handled.toList() shouldContainExactlyInAnyOrder listOf("A1", "A2", "A3", "A4")
                }
            }
        }

        feature("acknowledging by hand").config(enabled = AmqpTestBroker.available) {
            scenario("a manual consumer decides what leaves the queue") {
                withQueues { amqp, queue, dead ->
                    amqp.publish(queue, Order("keep"), Order("reject"))
                    val seen = CompletableDeferred<Unit>()
                    val handled = ConcurrentLinkedQueue<String>()

                    coroutineScope {
                        val consuming =
                            launch {
                                amqp.consumer<Order>(queue, ConsumerOptions(ack = AckStrategy.Manual)).use { consumer ->
                                    consumer.messages().collect { message ->
                                        handled += message.body.id
                                        if (message.body.id == "keep") consumer.ack(message) else consumer.nack(message)
                                        if (handled.size == 2) seen.complete(Unit)
                                    }
                                }
                            }

                        withTimeout(10.seconds) { seen.await() }
                        consuming.cancel()
                    }

                    amqp.eventually(queue, 0)
                    amqp.eventually(dead, 1)
                }
            }
        }
    })
