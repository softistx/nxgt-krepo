package com.softistx.amqp.publisher

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConfirmCallback
import com.rabbitmq.client.ShutdownListener
import com.rabbitmq.client.ShutdownSignalException
import com.softistx.amqp.AmqpClosedException
import com.softistx.amqp.codec.AmqpCodec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

/**
 * A channel that dies under a publisher with confirms outstanding.
 *
 * The client recovers a lost connection by default and restarts the channel's sequence numbers at
 * 1, so a confirm map keyed by sequence that outlives the loss would answer the first publish
 * after recovery with the *old* messages' entry. The channel here is a recording stand-in: a real
 * broker confirms in microseconds, which leaves nothing outstanding to lose, and the only thing
 * under test is what the publisher does when its shutdown listener fires.
 */
class ChannelLossTest :
    FeatureSpec({

        class Stub {
            val sequence = AtomicLong(1)
            val published = AtomicInteger()

            @Volatile
            var shutdown: ShutdownListener? = null

            @Volatile
            var acked: ConfirmCallback? = null

            val channel: Channel =
                Proxy.newProxyInstance(Channel::class.java.classLoader, arrayOf(Channel::class.java)) { _, method, args ->
                    when (method.name) {
                        "addShutdownListener" -> {
                            shutdown = args[0] as ShutdownListener
                            null
                        }
                        "addConfirmListener" -> {
                            acked = args[0] as ConfirmCallback
                            null
                        }
                        "getNextPublishSeqNo" -> sequence.get()
                        "basicPublish" -> {
                            published.incrementAndGet()
                            sequence.incrementAndGet()
                        }
                        else -> null
                    }.let { if (method.returnType == Long::class.javaPrimitiveType && it !is Long) 0L else it }
                } as Channel
        }

        feature("a channel lost while confirms are outstanding") {
            scenario("every outstanding publish fails with AmqpClosedException") {
                val stub = Stub()
                val publisher = AmqpPublisher(stub.channel, "orders", AmqpCodec.text, PublisherOptions())

                coroutineScope {
                    val first = async { runCatching { publisher.publish("a") } }
                    val second = async { runCatching { publisher.publish("b") } }
                    withTimeout(10.seconds) { while (stub.published.get() < 2) delay(10) }

                    stub.shutdown!!.shutdownCompleted(ShutdownSignalException(true, false, null, stub.channel))

                    withTimeout(10.seconds) {
                        shouldThrow<AmqpClosedException> { first.await().getOrThrow() }
                        shouldThrow<AmqpClosedException> { second.await().getOrThrow() }
                    }
                }
                publisher.close()
                stub.published.get() shouldBe 2
            }

            scenario("a confirm for a restarted sequence number settles only the publish made after the loss") {
                val stub = Stub()
                val publisher = AmqpPublisher(stub.channel, "orders", AmqpCodec.text, PublisherOptions())

                coroutineScope {
                    val before = async { runCatching { publisher.publish("before") } }
                    withTimeout(10.seconds) { while (stub.published.get() < 1) delay(10) }

                    stub.shutdown!!.shutdownCompleted(ShutdownSignalException(true, false, null, stub.channel))
                    stub.sequence.set(1)

                    val after = async { runCatching { publisher.publish("after") } }
                    withTimeout(10.seconds) { while (stub.published.get() < 2) delay(10) }
                    // Tag 1 again, as the recovered channel would number it.
                    stub.acked!!.handle(1, false)

                    withTimeout(10.seconds) {
                        shouldThrow<AmqpClosedException> { before.await().getOrThrow() }
                        after.await().getOrThrow().sequence shouldBe 1L
                    }
                }
                publisher.close()
            }
        }
    })
