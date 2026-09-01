package com.softistx.kafka.consumer

import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.util.TreeSet

/**
 * What is safe to commit when records finish out of order.
 *
 * A committed offset means "everything below this is done", so the only offset a consumer may
 * commit is one where that is true. With [Concurrency.PerPartition] the handlers finish in whatever
 * order they finish: 5, then 7, while 6 is still running. Committing 8 there would mean a crash
 * loses record 6 for good, and the group would never see it again.
 *
 * So this keeps, per partition, the offset everything below has completed — the contiguous prefix —
 * and moves it up only when the gaps close. It is deliberately a plain object with no Kafka calls
 * in it: the rule is subtle enough to be worth testing on its own.
 *
 * Unsynchronized on purpose: the poll loop is its only caller. A handler that finishes posts a
 * message and the loop applies it on its next turn, so completions, commits and revocations reach
 * this in one order rather than racing — see [KafkaSubscriber]. A lock here would make each
 * operation safe and still leave the interesting pair — complete, then commit — unordered.
 */
internal class OffsetTracker {
    private val completed = mutableMapOf<TopicPartition, TreeSet<Long>>()
    private val committable = mutableMapOf<TopicPartition, Long>()

    /** Records that [offset] on [partition] has been handled. */
    fun completed(
        partition: TopicPartition,
        offset: Long,
    ) {
        val pending = completed.getOrPut(partition) { TreeSet() }
        pending += offset

        // Walk the run of consecutive offsets from where we last stopped.
        var next = committable[partition] ?: pending.first()
        while (pending.remove(next)) next++
        committable[partition] = next
    }

    /**
     * What to commit now: for each partition, the offset the group should resume from — one past
     * the last contiguously completed record, which is what Kafka means by a committed offset.
     */
    fun committable(): Map<TopicPartition, OffsetAndMetadata> = committable.mapValues { (_, next) -> OffsetAndMetadata(next) }

    /** Whether anything has completed that has not been committed yet. */
    fun hasPending(): Boolean = committable.isNotEmpty()

    /** Forgets what has been committed, so the next commit does not repeat it. */
    fun committed(offsets: Map<TopicPartition, OffsetAndMetadata>) {
        offsets.forEach { (partition, offset) ->
            if (committable[partition] == offset.offset()) committable.remove(partition)
        }
    }

    /**
     * Drops everything about [partitions] — they belong to another consumer now.
     *
     * Called on revocation *after* the final commit: holding on would mean committing an offset for
     * a partition this consumer no longer owns, which the coordinator rejects and which would be
     * wrong even if it did not.
     */
    fun forget(partitions: Collection<TopicPartition>) {
        partitions.forEach {
            completed.remove(it)
            committable.remove(it)
        }
    }
}
