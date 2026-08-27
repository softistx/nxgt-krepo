package com.strange.kafka.consumer

import com.strange.kafka.Kafka
import com.strange.kafka.clientProperties
import com.strange.kafka.record.KafkaRecord
import com.strange.kafka.serde.KafkaSerde
import com.strange.kafka.serde.jsonSerde
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.time.toJavaDuration

/**
 * Records in, as a `Flow` or as a handler, with the offsets looked after.
 *
 * ```kotlin
 * kafka.subscriber<OrderEvent>("billing", listOf("orders")).use { orders ->
 *     orders.process { record -> charge(record.value) }
 * }
 * ```
 *
 * Four things about Kafka's consumer shape this exists to deal with:
 *
 * **The client belongs to one thread, and it means the thread, not the turn.** `KafkaConsumer`
 * compares the identity of the calling thread and throws `ConcurrentModificationException` when it
 * changes, so `Dispatchers.IO.limitedParallelism(1)` is not a substitute: it serializes access but
 * resumes on whichever thread is free. Hence one dispatcher over one thread — a *virtual* one, so
 * an application with a dozen subscribers does not pay a dozen platform threads to sit in `poll`.
 * Everything above it is ordinary coroutines: the loop is a coroutine, records cross a `Channel`,
 * handlers are suspend functions on the caller's own dispatcher.
 *
 * **Nothing except the loop is allowed to want that thread.** A poll holds it for the whole poll
 * timeout, so anything that waits on it waits that long — and if the loop never suspends, forever.
 * Offsets are therefore tracked under a lock rather than on the consumer's thread, and a commit
 * from a handler is a request the loop picks up on its next turn.
 *
 * **A slow handler must not cost the group its membership.** The loop never stops polling: when the
 * buffer between it and the handler fills, it pauses its partitions and keeps calling `poll`, which
 * then returns nothing but goes on proving this consumer is alive. Simply not polling while the
 * handler works is what gets a consumer evicted mid-batch and its records handed to someone else —
 * the failure that looks like duplicate processing under load and is really a rebalance storm.
 *
 * **A rebalance is where offsets are lost.** Partitions are committed on revocation, before they
 * are handed over, and forgotten afterwards.
 *
 * Delivery is at least once: every commit happens after the handler returned. Handlers have to be
 * idempotent, and the alternative — committing first — trades duplicates for silently dropped
 * records, which is the worse half of the same coin.
 */
class KafkaSubscriber<K, V> internal constructor(
    internal val consumer: Consumer<K, V>,
    private val topics: List<String>,
    private val options: SubscriberOptions,
) : AutoCloseable {
    /**
     * One virtual thread, and the same one every time.
     *
     * Virtual because a blocking `poll` then unmounts instead of holding a platform thread; single
     * because that is the client's contract. Do not "simplify" this to a shared dispatcher with
     * limited parallelism — see the note on identity above.
     */
    private val thread =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name("kafka-subscriber-", 0).factory())

    private val dispatcher: CoroutineDispatcher = thread.asCoroutineDispatcher()

    private val tracker = OffsetTracker()

    /** Commits asked for by a handler or a collector, for the loop to carry out and answer. */
    private val commitRequests = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)

    private val handledSinceCommit = AtomicInteger()

    @Volatile
    private var commitRequested = false

    @Volatile
    private var polling = false

    @Volatile
    private var assigned: Set<TopicPartition> = emptySet()

    /** Touched only by the loop. */
    private var lastCommit = TimeSource.Monotonic.markNow()

    /**
     * The partitions this consumer currently owns — empty before the first poll.
     *
     * Recorded by the rebalance listener, which Kafka calls on the consumer's own thread, rather
     * than read back from the client: asking the client would mean waiting for the poll loop to let
     * go of it.
     */
    val assignment: Set<TopicPartition> get() = assigned

    /**
     * The records, as they arrive, until the collector stops.
     *
     * Nothing is committed here — emitting a record is not handling it, and only the caller knows
     * when it has been. [commit] and [commitPending] are how a flow collector says so; [process] is
     * the version that says it for you.
     */
    fun records(): Flow<KafkaRecord<K, V>> =
        flow {
            val buffer = Channel<KafkaRecord<K, V>>(options.prefetch)
            coroutineScope {
                val poller = launch(dispatcher) { pollInto(buffer) }
                try {
                    for (record in buffer) emit(record)
                } finally {
                    poller.cancelAndJoin()
                }
            }
        }

    /**
     * Hands every record to [handler] and commits on the terms [SubscriberOptions.commit] set.
     *
     * With [Concurrency.PerPartition] the partitions run concurrently and each stays in order, and
     * the offsets committed are the contiguous completed prefix of each — see [OffsetTracker] for
     * why anything else loses records.
     */
    suspend fun process(handler: suspend (KafkaRecord<K, V>) -> Unit) {
        when (val concurrency = options.concurrency) {
            is Concurrency.Sequential -> {
                records().collect { record ->
                    handler(record)
                    handled(record)
                }
            }

            is Concurrency.PerPartition -> {
                processPerPartition(concurrency, handler)
            }
        }
    }

    /**
     * Commits everything handled so far, whatever the strategy says, and waits for it.
     *
     * While records are being collected this is a request the loop carries out on its next turn —
     * the loop owns the client. With no collection running, the thread is idle and the commit
     * happens on it directly.
     */
    suspend fun commitPending() {
        if (!polling) {
            withContext(dispatcher) { commitNow() }
            return
        }
        val committed = CompletableDeferred<Unit>()
        commitRequests.send(committed)
        committed.await()
    }

    /** Marks one record handled and commits up to it — for a [CommitStrategy.Manual] collector. */
    suspend fun commit(record: KafkaRecord<K, V>) {
        tracker.completed(TopicPartition(record.topic, record.partition), record.offset)
        commitPending()
    }

    /**
     * Stops the loop and closes the client.
     *
     * `wakeup` is the one method Kafka documents as safe to call from another thread: it makes an
     * in-flight `poll` throw, which is how a loop that is otherwise blocked gets told to stop. The
     * close itself is queued onto the consumer's own thread, behind the loop.
     */
    override fun close() {
        consumer.wakeup()
        runCatching { thread.execute { runCatching { consumer.close() } } }
        thread.shutdown()
    }

    // ─── The loop ─────────────────────────────────────────────────────────────

    private suspend fun pollInto(buffer: SendChannel<KafkaRecord<K, V>>) {
        val waiting = ArrayDeque<KafkaRecord<K, V>>()
        val awaiting = mutableListOf<CompletableDeferred<Unit>>()
        polling = true
        consumer.subscribe(topics, RevocationCommit())
        try {
            while (currentCoroutineContext().isActive) {
                while (waiting.isNotEmpty() && buffer.trySend(waiting.first()).isSuccess) {
                    waiting.removeFirst()
                }

                // Full buffer: stop being given records, but go on proving we are alive.
                if (waiting.isNotEmpty()) {
                    consumer.pause(consumer.assignment())
                } else if (consumer.paused().isNotEmpty()) {
                    consumer.resume(consumer.paused())
                }

                while (true) awaiting += commitRequests.tryReceive().getOrNull() ?: break

                val asked = commitRequested
                if (asked) commitRequested = false

                val timeout =
                    when {
                        asked || awaiting.isNotEmpty() -> Duration.ZERO
                        waiting.isNotEmpty() -> PAUSED_POLL
                        else -> options.pollTimeout
                    }
                consumer.poll(timeout.toJavaDuration()).forEach { waiting.addLast(KafkaRecord.from(it)) }

                if (asked || awaiting.isNotEmpty() || batchDue()) {
                    commitNow()
                    awaiting.forEach { it.complete(Unit) }
                    awaiting.clear()
                }
            }
        } catch (_: WakeupException) {
            // close() asked for this.
        } finally {
            polling = false
            runCatching { commitNow() }
            // Nobody else will answer these now.
            awaiting.forEach { it.complete(Unit) }
            while (true) (commitRequests.tryReceive().getOrNull() ?: break).complete(Unit)
        }
    }

    private suspend fun processPerPartition(
        concurrency: Concurrency.PerPartition,
        handler: suspend (KafkaRecord<K, V>) -> Unit,
    ) = coroutineScope {
        val partitions = mutableMapOf<TopicPartition, SendChannel<KafkaRecord<K, V>>>()
        val inFlight = Semaphore(concurrency.limit)

        records().collect { record ->
            val partition = TopicPartition(record.topic, record.partition)
            partitions
                .getOrPut(partition) {
                    Channel<KafkaRecord<K, V>>(Channel.BUFFERED).also { queue ->
                        launch {
                            /* One worker per partition, so a partition's records stay in order
                               while other partitions run alongside it. */
                            for (queued in queue) {
                                inFlight.withPermit { handler(queued) }
                                handled(queued)
                            }
                        }
                    }
                }.send(record)
        }
    }

    /**
     * Marks a record handled. Deliberately not suspending and deliberately not on the consumer's
     * thread: a handler that had to wait for the poll loop would be a handler throttled to one
     * record per poll timeout.
     */
    private fun handled(record: KafkaRecord<K, V>) {
        tracker.completed(TopicPartition(record.topic, record.partition), record.offset)
        handledSinceCommit.incrementAndGet()
        if (options.commit is CommitStrategy.AfterEach) commitRequested = true
    }

    private fun batchDue(): Boolean {
        val strategy = options.commit
        if (strategy !is CommitStrategy.Batched || !tracker.hasPending()) return false
        return handledSinceCommit.get() >= strategy.count || lastCommit.elapsedNow() >= strategy.every
    }

    /** Only ever called on the consumer's thread. */
    private fun commitNow() {
        if (!tracker.hasPending()) return
        val offsets = tracker.committable()
        consumer.commitSync(offsets)
        tracker.committed(offsets)
        handledSinceCommit.set(0)
        lastCommit = TimeSource.Monotonic.markNow()
    }

    /**
     * Kafka calls this inside `poll`, on the client's own thread, and waits for it — which is
     * exactly the window in which a partition's last offsets can still be committed by the
     * consumer that owns it.
     */
    private inner class RevocationCommit : ConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
            commitNow()
            tracker.forget(partitions)
            assigned = assigned - partitions.toSet()
        }

        override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
            assigned = consumer.assignment().toSet()
        }

        override fun onPartitionsLost(partitions: Collection<TopicPartition>) {
            /* Lost, not revoked: the partitions are already someone else's, and committing their
               offsets now would be claiming work this consumer no longer owns. */
            tracker.forget(partitions)
            assigned = assigned - partitions.toSet()
        }
    }

    private companion object {
        /** How long a paused poll waits — long enough not to spin, short enough to notice room. */
        val PAUSED_POLL = 100.milliseconds
    }
}

/**
 * A subscriber of `V` for [group], keyed by `String`, deserialized with kotlinx.serialization
 * through this cluster's `Json`.
 *
 * ```kotlin
 * kafka.subscriber<OrderEvent>("billing", listOf("orders"))
 * ```
 */
inline fun <reified V> Kafka.subscriber(
    group: String,
    topics: List<String>,
    options: SubscriberOptions = SubscriberOptions(),
    keySerde: KafkaSerde<String> = KafkaSerde.string,
    json: Json = this.json,
): KafkaSubscriber<String, V> = subscriber(group, topics, jsonSerde<V>(json), keySerde, options)

/** The same subscriber, named by its serde — for a value that is not JSON, or a `V` that cannot be reified. */
fun <K, V> Kafka.subscriber(
    group: String,
    topics: List<String>,
    valueSerde: KafkaSerde<V>,
    keySerde: KafkaSerde<K>,
    options: SubscriberOptions = SubscriberOptions(),
): KafkaSubscriber<K, V> =
    KafkaSubscriber(
        KafkaConsumer(
            config
                .clientProperties(*options.asProperties(group))
                .apply { putAll(options.properties) },
            keySerde.deserializer,
            valueSerde.deserializer,
        ),
        topics,
        options,
    )

internal fun SubscriberOptions.asProperties(group: String): Array<Pair<String, Any>> =
    buildList {
        add(ConsumerConfig.GROUP_ID_CONFIG to group)
        add(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to offsetReset.value)
        /* Never Kafka's own auto-commit: it commits on a timer whether or not the handler ran,
           which turns at-least-once into "whatever had been polled". CommitStrategy is the answer. */
        add(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false)
        maxPollRecords?.let { add(ConsumerConfig.MAX_POLL_RECORDS_CONFIG to it) }
    }.toTypedArray()
