package com.strange.kafka.consumer

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
 * Synchronized because of who calls it: handlers running on whatever dispatcher the caller is
 * using, and the poll loop on the consumer's own thread. The critical sections are a few list
 * operations, so a lock is cheaper than routing every completion through the consumer thread —
 * which is also what would let a blocking poll stall every handler behind it.
 */
internal class OffsetTracker {
    private val completed = mutableMapOf<TopicPartition, TreeSet<Long>>()
    private val committable = mutableMapOf<TopicPartition, Long>()

    /** Records that [offset] on [partition] has been handled. */
    @Synchronized
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
    @Synchronized
    fun committable(): Map<TopicPartition, OffsetAndMetadata> = committable.mapValues { (_, next) -> OffsetAndMetadata(next) }

    /** Whether anything has completed that has not been committed yet. */
    @Synchronized
    fun hasPending(): Boolean = committable.isNotEmpty()

    /** Forgets what has been committed, so the next commit does not repeat it. */
    @Synchronized
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
    @Synchronized
    fun forget(partitions: Collection<TopicPartition>) {
        partitions.forEach {
            completed.remove(it)
            committable.remove(it)
        }
    }
}
