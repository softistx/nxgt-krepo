package com.softistx.kafka.consumer

import com.softistx.kafka.record.KafkaRecord
import org.apache.kafka.common.TopicPartition
import java.util.concurrent.atomic.AtomicReference

/**
 * Which partitions this consumer owns, and since when.
 *
 * The poll loop is the only writer, inside `poll`, where Kafka runs the rebalance listener; the
 * collector of `records()` is a reader on another thread, which is why the state is one immutable
 * map behind an atomic reference rather than a set the two share. Each assignment of a partition
 * gets a new epoch, so a record stamped before a revocation is not current after it, even when the
 * same partition comes back in the next rebalance.
 *
 * This is what a revocation uses instead of reaching into the buffer between the loop and the
 * collector: taking records out of a channel its collector is receiving from puts the ones it keeps
 * back behind records it has already taken.
 */
internal class Ownership {
    private val epochs = AtomicReference<Map<TopicPartition, Long>>(emptyMap())

    /** Touched only by the loop. */
    private var last = 0L

    /** The loop's, from `onPartitionsAssigned`. */
    fun assigned(partitions: Collection<TopicPartition>) {
        epochs.set(epochs.get() + partitions.associateWith { ++last })
    }

    /** The loop's, from `onPartitionsRevoked` and `onPartitionsLost`. */
    fun revoked(partitions: Collection<TopicPartition>) {
        epochs.set(epochs.get() - partitions.toSet())
    }

    fun owns(partition: TopicPartition): Boolean = partition in epochs.get()

    /** Whether a completion stamped [epoch] still belongs to [partition]'s current assignment. */
    fun accepts(
        partition: TopicPartition,
        epoch: Long,
    ): Boolean {
        val current = epochs.get()[partition] ?: return false
        return epoch == KafkaRecord.NO_EPOCH || epoch == current
    }

    /** [record] as owned right now, or `null` when its partition is not. */
    fun <K, V> stamp(record: KafkaRecord<K, V>): OwnedRecord<K, V>? =
        epochs.get()[TopicPartition(record.topic, record.partition)]?.let { OwnedRecord(record, it) }

    /** Whether the partition of [owned] is still the one it was stamped under. */
    fun isCurrent(owned: OwnedRecord<*, *>): Boolean =
        epochs.get()[TopicPartition(owned.record.topic, owned.record.partition)] == owned.epoch
}

/** A record and the epoch of its partition's assignment when it was handed to the buffer. */
internal class OwnedRecord<K, V>(
    val record: KafkaRecord<K, V>,
    val epoch: Long,
)
