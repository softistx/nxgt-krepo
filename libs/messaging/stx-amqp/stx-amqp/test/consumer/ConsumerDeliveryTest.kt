package com.softistx.amqp.consumer

import com.rabbitmq.client.Channel
import com.softistx.amqp.AmqpBufferFullException
import com.softistx.amqp.AmqpTestBroker
import com.softistx.amqp.topology.messageCount
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class Note(
    val id: String = "x",
)

/**
 * A real [AmqpConsumer] whose collector stalls, and what that does to a sibling consumer on the same
 * connection — the consumer-level counterpart of `DeliveryThreadsTest`, which asked the client.
 *
 * Both scenarios give the connection a two-thread delivery pool: a delivery callback parked on a
 * full buffer would take one of them, and two such would starve the sibling.
 */
class ConsumerDeliveryTest :
    FeatureSpec({

        /** Declares a fresh queue on [setup], publishes [count] messages to it and returns its name. */
        fun fresh(
            setup: Channel,
            prefix: String,
            count: Int,
        ): String {
            val queue = AmqpTestBroker.name(prefix)
            setup.queueDeclare(queue, true, false, false, null)
            repeat(count) { setup.basicPublish("", queue, null, "{}".toByteArray()) }
            return queue
        }

        feature("a consumer whose collector stalls").config(enabled = AmqpTestBroker.available) {
            /* Guards against a regression, not the cap itself (ConsumerOptionsTest has that): a consumer at
               its prefetch cap, stalled, never parks a delivery thread, so the sibling is still served. */
            scenario("at the prefetch cap, it leaves a sibling consumer served") {
                val executor = Executors.newFixedThreadPool(2) { Thread(it, "spec-delivery").apply { isDaemon = true } }
                val hold = CompletableDeferred<Unit>()
                try {
                    connected(executor) { amqp ->
                        val setup = amqp.openChannel()
                        val stalled = fresh(setup, "stalled", 200)
                        val delivered = CompletableDeferred<String>()
                        val sibling = sibling(amqp, delivered)
                        val consumer =
                            amqp.consumer<Note>(stalled, ConsumerOptions(prefetch = ConsumerOptions.MAX_PREFETCH))
                        try {
                            coroutineScope {
                                val collecting = launch(IO) { consumer.messages().collect { hold.await() } }
                                // The broker has handed over its prefetch: one held, the rest buffered.
                                withTimeout(10.seconds) {
                                    while (amqp.messageCount(stalled) > 200L - ConsumerOptions.MAX_PREFETCH) delay(20)
                                }
                                val thread = withTimeout(5.seconds) { delivered.await() }
                                println("Threads: sibling served on [$thread] beside a stalled consumer")
                                thread shouldBe "spec-delivery"
                                amqp.messageCount(stalled) shouldBe 200L - ConsumerOptions.MAX_PREFETCH
                                collecting.cancel()
                            }
                        } finally {
                            consumer.close()
                            sibling.close()
                            runCatching { setup.queueDelete(stalled) }
                            runCatching { setup.close() }
                        }
                    }
                } finally {
                    executor.shutdownNow()
                }
            }

            scenario("on auto-ack, it fails with the buffer's contents received first and parks no pool thread") {
                val executor = Executors.newFixedThreadPool(2) { Thread(it, "spec-delivery").apply { isDaemon = true } }
                val hold = CompletableDeferred<Unit>()
                val received = ConcurrentLinkedQueue<String>()
                try {
                    connected(executor) { amqp ->
                        val setup = amqp.openChannel()
                        val queue = fresh(setup, "autoack", 400)
                        val delivered = CompletableDeferred<String>()
                        val sibling = sibling(amqp, delivered)
                        val consumer = amqp.consumer<Note>(queue, ConsumerOptions(ack = AckStrategy.OnDelivery))
                        try {
                            coroutineScope {
                                val outcome =
                                    async(IO) {
                                        runCatching {
                                            consumer.messages().collect {
                                                received += it.body.id
                                                hold.await()
                                            }
                                        }
                                    }
                                // No prefetch on auto-ack: the broker pushes all 400 and the buffer overflows.
                                withTimeout(10.seconds) { while (amqp.messageCount(queue) > 0L) delay(20) }
                                withTimeout(5.seconds) { delivered.await() } shouldBe "spec-delivery"
                                hold.complete(Unit)
                                val failure = withTimeout(10.seconds) { outcome.await() }.exceptionOrNull()
                                println("Threads: auto-ack stalled collector failed with [${failure?.message}]")
                                failure.shouldBeInstanceOf<AmqpBufferFullException>().capacity shouldBe 128
                                // The one the collector held, then the 128 the buffer held, before the failure.
                                received.size shouldBe 1 + 128
                            }
                        } finally {
                            consumer.close()
                            sibling.close()
                            runCatching { setup.queueDelete(queue) }
                            runCatching { setup.close() }
                        }
                    }
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    })
