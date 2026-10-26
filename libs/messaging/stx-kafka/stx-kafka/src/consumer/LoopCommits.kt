package com.softistx.kafka.consumer

import com.softistx.common.coroutines.Mailbox
import kotlinx.coroutines.CompletableDeferred
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.common.TopicPartition
import kotlin.time.TimeSource

/**
 * The loop's commit bookkeeping: applies the [mailbox] messages, decides when a commit is due and
 * answers the callers waiting for one.
 *
 * Touched only on the subscriber's dispatcher, which makes the loop the only writer of [tracker].
 */
internal class LoopCommits<K, V>(
    private val consumer: Consumer<K, V>,
    private val options: SubscriberOptions,
    private val mailbox: Mailbox<LoopMessage>,
    private val ownership: Ownership,
) {
    private val tracker = OffsetTracker()

    /** Commits asked for and not yet answered. */
    private val awaiting = mutableListOf<CompletableDeferred<Unit>>()

    private var handledSinceCommit = 0

    private var lastCommit = TimeSource.Monotonic.markNow()

    /** Applies everything handlers and collectors have asked for since the last turn. */
    fun drainMailbox() {
        while (true) {
            when (val message = mailbox.tryReceive()) {
                null -> {
                    return
                }

                is LoopMessage.Completed -> {
                    /* A record handled after its partition was revoked is not ours to commit:
                       recording it would put the partition back in the tracker after forget. */
                    if (ownership.accepts(message.partition, message.epoch)) {
                        tracker.completed(message.partition, message.offset)
                        handledSinceCommit++
                    }
                }

                is LoopMessage.CommitNow -> {
                    awaiting += message.done
                }
            }
        }
    }

    fun commitDue(): Boolean {
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
    fun serveCommits() {
        drainMailbox()
        commitAndAnswer()
    }

    fun commitAndAnswer() {
        try {
            commitNow()
        } catch (failure: Throwable) {
            answer { it.completeExceptionally(failure) }
            throw failure
        }
        answer { it.complete(Unit) }
    }

    fun answer(outcome: (CompletableDeferred<Unit>) -> Unit) {
        awaiting.forEach(outcome)
        awaiting.clear()
    }

    fun commitNow() {
        if (!tracker.hasPending()) return
        val offsets = tracker.committable()
        consumer.commitSync(offsets)
        tracker.committed(offsets)
        handledSinceCommit = 0
        lastCommit = TimeSource.Monotonic.markNow()
    }

    fun forget(partitions: Collection<TopicPartition>) = tracker.forget(partitions)
}
