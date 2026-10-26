package com.softistx.kafka.consumer

import com.softistx.common.coroutines.Mailbox
import com.softistx.common.lifecycle.CloseGuard
import com.softistx.kafka.record.KafkaRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.TopicPartition

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
    private val mailbox = Mailbox<LoopMessage>()

    private val closeGuard = CloseGuard()

    private val polling = MutableStateFlow(false)

    private val assigned = MutableStateFlow<Set<TopicPartition>>(emptySet())

    /** What this consumer owns, as the rebalance listener last said — what records and completions are checked against. */
    private val ownership = Ownership()

    // Touched only on the dispatcher.
    private val commits = LoopCommits(consumer, options, mailbox, ownership)

    private val loop = PollLoop(consumer, topics, options, ownership, commits, polling, assigned)

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
            val buffer = Channel<OwnedRecord<K, V>>(options.prefetch)
            coroutineScope {
                val poller = launch(dispatcher) { loop.pollInto(buffer) }
                try {
                    // A record whose partition was revoked since it was buffered is not ours to hand out.
                    for (owned in buffer) if (ownership.isCurrent(owned)) emit(owned.record.also { it.epoch = owned.epoch })
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
                records().processPerPartition(concurrency, handler, ::handled)
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
        mailbox.post(LoopMessage.CommitNow(committed))
        if (!polling.value) withContext(dispatcher) { commits.serveCommits() }
        committed.await()
    }

    /**
     * Marks one record handled and commits up to it — for a [CommitStrategy.Manual] collector.
     *
     * A `copy()` of the emitted record carries no assignment epoch, so it is accepted for the
     * partition's current assignment even when the original was emitted before a revocation.
     */
    suspend fun commit(record: KafkaRecord<K, V>) {
        mailbox.post(LoopMessage.Completed(TopicPartition(record.topic, record.partition), record.offset, record.epoch))
        commitPending()
    }

    /**
     * Stops the loop and closes the client.
     *
     * `wakeup` is the one method Kafka documents as safe to call from another thread: it makes an
     * in-flight `poll` throw, which is how a loop that is otherwise blocked gets told to stop. The
     * close itself is queued onto the dispatcher, where it lands behind the loop it just woke.
     */
    override fun close() =
        closeGuard.once {
            consumer.wakeup()
            scope
                .launch { runCatching { consumer.close() } }
                .invokeOnCompletion { scope.cancel() }
        }

    /**
     * Marks a record handled — a message, not a call. A handler that had to wait for the dispatcher
     * would be a handler throttled to one record per poll timeout, and one poll away from deadlock.
     */
    private fun handled(record: KafkaRecord<K, V>) {
        mailbox.post(LoopMessage.Completed(TopicPartition(record.topic, record.partition), record.offset, record.epoch))
    }
}
