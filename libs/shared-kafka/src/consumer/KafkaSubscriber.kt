package com.strange.kafka.consumer

import com.strange.kafka.Kafka
import com.strange.kafka.clientProperties
import com.strange.kafka.record.KafkaRecord
import com.strange.kafka.serde.KafkaSerde
import com.strange.kafka.serde.jsonSerde
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * **The client refuses overlapping calls, and only overlapping calls.** Every method takes an owner
 * slot on the way in and frees it on the way out, so a second caller *during* a call gets
 * `ConcurrentModificationException` while consecutive calls from different threads are fine — see
 * `ConsumerConfinementTest`, which establishes exactly that against the real client. What this needs
 * is therefore mutual exclusion, not a thread of its own, and `Dispatchers.IO.limitedParallelism(1)`
 * is precisely mutual exclusion: one task at a time, on whichever IO thread is free, which is where
 * a blocking `poll` belongs anyway.
 *
 * **Nothing except the loop is allowed to want that dispatcher.** A poll holds it for the whole poll
 * timeout, and the loop between turns never suspends, so anything that waits to be scheduled there
 * waits for the loop to finish — forever, while records keep arriving. Handlers therefore never
 * touch the dispatcher: a completion or a commit is a message posted to [mailbox], and the loop
 * applies it on its next turn. That also makes the loop the only writer of [OffsetTracker] and puts
 * a completion and the commit that must follow it in one queue instead of two racing flags.
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
     * One caller inside the client at a time — the client's whole requirement.
     *
     * A view over the IO pool rather than a thread of its own: nothing here is thread-affine, and a
     * subscriber that is idle costs no thread at all.
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** For [close], which has to outlive the loop it is stopping. */
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    /** What handlers and collectors ask of the loop. Unlimited, so asking never blocks a handler. */
    private val mailbox = Channel<LoopMessage>(Channel.UNLIMITED)

    private val polling = MutableStateFlow(false)

    private val assigned = MutableStateFlow<Set<TopicPartition>>(emptySet())

    // ─── Everything below is touched only on the dispatcher ───────────────────

    private val tracker = OffsetTracker()

    /** Commits asked for and not yet answered. */
    private val awaiting = mutableListOf<CompletableDeferred<Unit>>()

    private var handledSinceCommit = 0

    private var lastCommit = TimeSource.Monotonic.markNow()

    /**
     * The partitions this consumer currently owns — empty before the first poll.
     *
     * Read off the client by the loop, on the loop's own turn, where it costs nothing: any other
     * caller asking the client directly would have to wait for a poll to give it up. A flow rather
     * than a value because a rebalance is an event worth reacting to, and what the routing DSL's
     * `onAssigned` / `onRevoked` will watch.
     */
    val assignment: StateFlow<Set<TopicPartition>> get() = assigned.asStateFlow()

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
     * Posted to the loop, which owns the client and answers on its next turn. With no loop running
     * there is nobody to answer, and the dispatcher is free for this call to serve itself — asking
     * *before* checking is what makes a loop that stops in between harmless, since a loop on its way
     * out empties [mailbox] after it stops reporting itself as polling.
     */
    suspend fun commitPending() {
        val committed = CompletableDeferred<Unit>()
        mailbox.send(LoopMessage.CommitNow(committed))
        if (!polling.value) withContext(dispatcher) { serveCommits() }
        committed.await()
    }

    /** Marks one record handled and commits up to it — for a [CommitStrategy.Manual] collector. */
    suspend fun commit(record: KafkaRecord<K, V>) {
        mailbox.send(LoopMessage.Completed(TopicPartition(record.topic, record.partition), record.offset))
        commitPending()
    }

    /**
     * Stops the loop and closes the client.
     *
     * `wakeup` is the one method Kafka documents as safe to call from another thread: it makes an
     * in-flight `poll` throw, which is how a loop that is otherwise blocked gets told to stop. The
     * close itself is queued onto the dispatcher, where it lands behind the loop it just woke.
     */
    override fun close() {
        consumer.wakeup()
        scope
            .launch { runCatching { consumer.close() } }
            .invokeOnCompletion { scope.cancel() }
    }

    // ─── The loop ─────────────────────────────────────────────────────────────

    private suspend fun pollInto(buffer: SendChannel<KafkaRecord<K, V>>) {
        val waiting = ArrayDeque<KafkaRecord<K, V>>()
        polling.value = true
        consumer.subscribe(topics, RevocationCommit())
        try {
            while (currentCoroutineContext().isActive) {
                drainMailbox()

                while (waiting.isNotEmpty() && buffer.trySend(waiting.first()).isSuccess) {
                    waiting.removeFirst()
                }

                // Full buffer: stop being given records, but go on proving we are alive.
                if (waiting.isNotEmpty()) {
                    consumer.pause(consumer.assignment())
                } else if (consumer.paused().isNotEmpty()) {
                    consumer.resume(consumer.paused())
                }

                val due = commitDue()
                val timeout =
                    when {
                        due -> Duration.ZERO
                        waiting.isNotEmpty() -> PAUSED_POLL
                        else -> options.pollTimeout
                    }
                consumer.poll(timeout.toJavaDuration()).forEach { waiting.addLast(KafkaRecord.from(it)) }
                assigned.value = consumer.assignment().toSet()

                if (due) commitAndAnswer()
            }
        } catch (_: WakeupException) {
            // close() asked for this.
        } finally {
            /* Reported stopped before the last drain, so a commit that arrives after this either
               lands in the drain below or finds polling false and serves itself. */
            polling.value = false
            drainMailbox()
            runCatching { commitNow() }
            answer { it.complete(Unit) } // Nobody else will.
        }
    }

    /** Applies everything handlers and collectors have asked for since the last turn. */
    private fun drainMailbox() {
        while (true) {
            when (val message = mailbox.tryReceive().getOrNull()) {
                null -> {
                    return
                }

                is LoopMessage.Completed -> {
                    tracker.completed(message.partition, message.offset)
                    handledSinceCommit++
                }

                is LoopMessage.CommitNow -> {
                    awaiting += message.done
                }
            }
        }
    }

    private fun commitDue(): Boolean {
        if (awaiting.isNotEmpty()) return true
        if (!tracker.hasPending()) return false
        return when (val strategy = options.commit) {
            is CommitStrategy.AfterEach -> {
                handledSinceCommit > 0
            }

            is CommitStrategy.Batched -> {
                handledSinceCommit >= strategy.count || lastCommit.elapsedNow() >= strategy.every
            }

            is CommitStrategy.Manual -> {
                false
            }
        }
    }

    /** A whole turn for a commit asked for while no loop is running. */
    private fun serveCommits() {
        drainMailbox()
        commitAndAnswer()
    }

    private fun commitAndAnswer() {
        try {
            commitNow()
        } catch (failure: Throwable) {
            answer { it.completeExceptionally(failure) }
            throw failure
        }
        answer { it.complete(Unit) }
    }

    private fun answer(outcome: (CompletableDeferred<Unit>) -> Unit) {
        awaiting.forEach(outcome)
        awaiting.clear()
    }

    private fun commitNow() {
        if (!tracker.hasPending()) return
        val offsets = tracker.committable()
        consumer.commitSync(offsets)
        tracker.committed(offsets)
        handledSinceCommit = 0
        lastCommit = TimeSource.Monotonic.markNow()
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
     * Marks a record handled — a message, not a call. A handler that had to wait for the dispatcher
     * would be a handler throttled to one record per poll timeout, and one poll away from deadlock.
     */
    private fun handled(record: KafkaRecord<K, V>) {
        mailbox.trySend(LoopMessage.Completed(TopicPartition(record.topic, record.partition), record.offset))
    }

    /**
     * Kafka calls this inside `poll`, on the loop's own turn, and waits for it — which is exactly
     * the window in which a partition's last offsets can still be committed by the consumer that
     * owns it. Draining first is what includes the records handled during the poll being rebalanced.
     */
    private inner class RevocationCommit : ConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
            drainMailbox()
            runCatching { commitAndAnswer() }
            tracker.forget(partitions)
        }

        override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) = Unit

        override fun onPartitionsLost(partitions: Collection<TopicPartition>) {
            /* Lost, not revoked: the partitions are already someone else's, and committing their
               offsets now would be claiming work this consumer no longer owns. */
            tracker.forget(partitions)
        }
    }

    private companion object {
        /** How long a paused poll waits — long enough not to spin, short enough to notice room. */
        val PAUSED_POLL = 100.milliseconds
    }
}

/** What a handler or a collector asks of the poll loop, in the order it asked. */
private sealed interface LoopMessage {
    /** This record has been handled; its offset may be committed once the prefix below it is. */
    data class Completed(
        val partition: TopicPartition,
        val offset: Long,
    ) : LoopMessage

    /** Commit what is committable and say so. */
    data class CommitNow(
        val done: CompletableDeferred<Unit>,
    ) : LoopMessage
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
