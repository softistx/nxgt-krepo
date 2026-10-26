package com.softistx.kafka.consumer

import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.common.TopicPartition
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The loop, driven by a fake broker.
 *
 * A real cluster is what proves a rebalance behaves; a fake is what makes the *timing* claims
 * checkable at all — that a full buffer pauses the partitions instead of stalling the poll, that a
 * strategy commits when it says it does, and that partitions run concurrently while each stays in
 * order. Those are the parts a wrapper gets wrong silently.
 */
class KafkaSubscriberTest :
    FeatureSpec({

        val orders0 = TopicPartition("orders", 0)
        val orders1 = TopicPartition("orders", 1)

        fun subscriber(
            options: SubscriberOptions = SubscriberOptions(),
        ): Pair<KafkaSubscriber<String, String>, MockConsumer<String, String>> {
            val mock = MockConsumer<String, String>("earliest")
            return KafkaSubscriber(mock, listOf("orders"), options) to mock
        }

        /** A mock that keeps the rebalance listener, so a spec can revoke part of the assignment. */
        class ListenerMock : MockConsumer<String, String>("earliest") {
            @Volatile private var held: ConsumerRebalanceListener? = null

            override fun subscribe(
                topics: MutableCollection<String>,
                listener: ConsumerRebalanceListener,
            ) {
                held = listener
                super.subscribe(topics, listener)
            }

            fun listener(): ConsumerRebalanceListener = held!!
        }

        fun rebalancing(options: SubscriberOptions): Pair<KafkaSubscriber<String, String>, ListenerMock> {
            val mock = ListenerMock()
            return KafkaSubscriber(mock, listOf("orders"), options) to mock
        }

        fun MockConsumer<String, String>.assign(vararg partitions: TopicPartition) =
            schedulePollTask {
                rebalance(partitions.toList())
                updateBeginningOffsets(partitions.associateWith { 0L })
            }

        fun MockConsumer<String, String>.feed(
            partition: TopicPartition,
            values: List<String>,
            from: Long = 0,
        ) = schedulePollTask {
            values.forEachIndexed { index, value ->
                addRecord(ConsumerRecord(partition.topic(), partition.partition(), from + index, "k$index", value))
            }
        }

        suspend fun until(
            timeout: Duration = 5.seconds,
            condition: () -> Boolean,
        ) {
            withTimeout(timeout) {
                while (!condition()) delay(20)
            }
        }

        feature("reading records") {
            scenario("they arrive as a flow, in order, with where they came from") {
                val (subscriber, mock) = subscriber()
                mock.assign(orders0)
                mock.feed(orders0, listOf("v0", "v1", "v2"))

                val records = withTimeout(10.seconds) { subscriber.records().take(3).toList() }

                records.map { it.value } shouldContainExactly listOf("v0", "v1", "v2")
                records.map { it.offset } shouldContainExactly listOf(0L, 1L, 2L)
                records.first().topic shouldBe "orders"

                subscriber.close()
            }
        }

        feature("a handler slower than the broker") {
            scenario("the loop pauses its partitions rather than stopping polling") {
                /* The whole point: a consumer that simply stops calling poll while it works is
                   evicted from its group once max.poll.interval.ms passes, and its records are
                   handed to somebody else mid-batch. */
                val (subscriber, mock) = subscriber(SubscriberOptions(prefetch = 1))
                mock.assign(orders0)
                mock.feed(orders0, (0..20).map { "v$it" })

                coroutineScope {
                    val collector = launch { subscriber.records().collect { delay(30.seconds) } }

                    until { mock.paused().isNotEmpty() }
                    mock.paused() shouldBe setOf(orders0)

                    collector.cancel()
                }

                subscriber.close()
            }
        }

        feature("committing") {
            scenario("after each record, the group is one past the record just handled") {
                /* Also the guard against a deadlock this once had: the handler records its
                   completion while the loop is inside poll, so anything that makes a completion
                   wait for the consumer's thread hangs here rather than in production. */
                val (subscriber, mock) = subscriber(SubscriberOptions(commit = CommitStrategy.AfterEach))
                mock.assign(orders0)
                mock.feed(orders0, listOf("v0", "v1", "v2"))

                coroutineScope {
                    val processing = launch { subscriber.process { } }

                    until { mock.committed(setOf(orders0))[orders0]?.offset() == 3L }

                    processing.cancel()
                }

                subscriber.close()
            }

            scenario("in batches, nothing is committed until the batch is full") {
                val (subscriber, mock) =
                    subscriber(
                        SubscriberOptions(commit = CommitStrategy.Batched(count = 5, every = 1.seconds)),
                    )
                mock.assign(orders0)
                mock.feed(orders0, listOf("v0", "v1"))

                coroutineScope {
                    val handled = CompletableDeferred<Unit>()
                    val processing = launch { subscriber.process { if (it.offset == 1L) handled.complete(Unit) } }

                    handled.await()
                    mock.committed(setOf(orders0))[orders0] shouldBe null

                    mock.feed(orders0, listOf("v2", "v3", "v4"), from = 2)
                    until { mock.committed(setOf(orders0))[orders0]?.offset() == 5L }

                    processing.cancel()
                }

                subscriber.close()
            }
        }

        feature("reporting what it owns") {
            scenario("the assignment flow names the partitions it was given") {
                /* A flow rather than a getter: a caller can wait for a rebalance instead of polling
                   for one, which is what the routing DSL's onAssigned hook needs. */
                val (subscriber, mock) = subscriber()
                mock.assign(orders0, orders1)

                coroutineScope {
                    val collector = launch { subscriber.records().collect { } }

                    val owned = withTimeout(10.seconds) { subscriber.assignment.first { it.isNotEmpty() } }
                    owned shouldBe setOf(orders0, orders1)

                    collector.cancel()
                }

                subscriber.close()
            }
        }

        feature("closing") {
            scenario("it returns while the loop is polling rather than waiting for it") {
                val (subscriber, mock) = subscriber()
                mock.assign(orders0)

                coroutineScope {
                    val collector = launch { subscriber.records().collect { } }
                    until { mock.assignment().isNotEmpty() }

                    /* wakeup() is the one call Kafka allows from another thread; without it this
                       waits for a poll that is not coming back. */
                    withTimeout(10.seconds) { subscriber.close() }

                    // Closing ends the flow by itself: nobody cancels the collector here.
                    withTimeout(10.seconds) { collector.join() }
                }
            }

            scenario("it ends process() without the caller cancelling it") {
                val (subscriber, mock) = subscriber()
                mock.assign(orders0)

                coroutineScope {
                    val processing = launch { subscriber.process { } }
                    until { mock.assignment().isNotEmpty() }

                    subscriber.close()

                    withTimeout(10.seconds) { processing.join() }
                }
            }
        }

        feature("a rebalance that takes a partition away") {
            scenario("its buffered records are dropped, not delivered to the handler") {
                val (subscriber, mock) = subscriber(SubscriberOptions(prefetch = 1))
                mock.assign(orders0)
                mock.feed(orders0, (0..9).map { "a$it" })

                val seen = ConcurrentLinkedQueue<String>()
                val gate = Channel<Unit>(Channel.UNLIMITED)

                coroutineScope {
                    val collector =
                        launch {
                            subscriber.records().collect {
                                seen += it.value
                                gate.receive()
                            }
                        }

                    until { seen.size == 1 }
                    // One record in the handler's hands, one in the buffer, the rest waiting.
                    until { mock.paused().isNotEmpty() }

                    mock.schedulePollTask {
                        mock.updateBeginningOffsets(mapOf(orders1 to 0L))
                        mock.rebalance(listOf(orders1))
                    }
                    until { subscriber.assignment.value == setOf(orders1) }
                    mock.schedulePollTask {
                        mock.addRecord(ConsumerRecord("orders", 1, 0L, "k", "b0"))
                    }

                    repeat(20) { gate.trySend(Unit) }
                    until { seen.contains("b0") }

                    seen.toList() shouldContainExactly listOf("a0", "b0")
                    collector.cancel()
                }

                subscriber.close()
            }

            scenario("a record handled after its partition was revoked is not committed") {
                val (subscriber, mock) = subscriber(SubscriberOptions(commit = CommitStrategy.Manual))
                mock.assign(orders0)
                mock.feed(orders0, listOf("a0"))

                val first = subscriber.records().first()
                first.value shouldBe "a0"

                mock.schedulePollTask {
                    mock.updateBeginningOffsets(mapOf(orders1 to 0L))
                    mock.rebalance(listOf(orders1))
                }
                coroutineScope {
                    val collector = launch { subscriber.records().collect { } }
                    until { subscriber.assignment.value == setOf(orders1) }
                    collector.cancel()
                }

                subscriber.commit(first)

                mock.committed(setOf(orders0))[orders0] shouldBe null

                subscriber.close()
            }
        }

        feature("a revocation of one partition") {
            scenario("keeps the order of the partitions it leaves, and never hands over the revoked one") {
                val (subscriber, mock) = rebalancing(SubscriberOptions(prefetch = 8, commit = CommitStrategy.AfterEach))
                mock.assign(orders0, orders1)
                mock.feed(orders1, (0 until 300).map { "b$it" })
                mock.feed(orders0, (0 until 300).map { "a$it" })

                val handled = ConcurrentLinkedQueue<String>()
                coroutineScope {
                    val processing = launch { subscriber.process { handled += it.value } }
                    until { handled.size > 5 }
                    // Cooperative: only orders0 goes, in the middle of a poll, as Kafka does it.
                    mock.schedulePollTask { mock.listener().onPartitionsRevoked(listOf(orders0)) }
                    until(10.seconds) { handled.count { it.startsWith("b") } == 300 }
                    processing.cancel()
                }

                handled.filter { it.startsWith("b") } shouldContainExactly (0 until 300).map { "b$it" }
                subscriber.commitPending()
                mock.committed(setOf(orders1))[orders1]?.offset() shouldBe 300L
                /* Nothing of orders0 reaches the handler out of order or after it was revoked: what
                   was handled is a prefix, and the commit never passes it. */
                val a = handled.filter { it.startsWith("a") }
                a shouldContainExactly (0 until a.size).map { "a$it" }
                (mock.committed(setOf(orders0))[orders0]?.offset() ?: 0L) shouldBeLessThanOrEqual a.size.toLong()

                subscriber.close()
            }
        }

        feature("handling partitions concurrently") {
            scenario("process returns after close, with the partition workers ended") {
                val (subscriber, mock) =
                    subscriber(SubscriberOptions(concurrency = Concurrency.PerPartition(limit = 4)))
                mock.assign(orders0, orders1)
                mock.feed(orders0, listOf("a0"))
                mock.feed(orders1, listOf("b0"))

                val handled = ConcurrentLinkedQueue<String>()
                coroutineScope {
                    val processing = launch { subscriber.process { handled += it.value } }
                    until { handled.size == 2 }
                    subscriber.close()
                    withTimeout(5.seconds) { processing.join() }
                }
            }

            scenario("they run alongside each other while each keeps its own order") {
                val (subscriber, mock) =
                    subscriber(
                        SubscriberOptions(concurrency = Concurrency.PerPartition(limit = 4), prefetch = 16),
                    )
                mock.assign(orders0, orders1)
                mock.feed(orders0, listOf("a0", "a1"))
                mock.feed(orders1, listOf("b0", "b1"))

                val handled = ConcurrentLinkedQueue<String>()
                val started = TimeSource.Monotonic.markNow()

                coroutineScope {
                    val processing =
                        launch {
                            subscriber.process { record ->
                                delay(200)
                                handled += record.value
                            }
                        }

                    until(10.seconds) { handled.size == 4 }
                    processing.cancel()
                }

                /* Four records, 200ms each: sequential would be 800ms. Two partitions running
                   alongside each other is ~400ms. */
                started.elapsedNow().inWholeMilliseconds shouldBeLessThan 700L

                handled.filter { it.startsWith("a") } shouldContainExactly listOf("a0", "a1")
                handled.filter { it.startsWith("b") } shouldContainExactly listOf("b0", "b1")

                subscriber.close()
            }
        }
    })
