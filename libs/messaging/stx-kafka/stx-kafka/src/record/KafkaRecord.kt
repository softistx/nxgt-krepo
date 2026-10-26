package com.softistx.kafka.record

import com.softistx.kafka.KafkaValueException
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.time.Instant

/**
 * One record, and where it came from.
 *
 * [value] is nullable and stays nullable: on a compacted topic a null value is a **tombstone**, the
 * record that says this key is deleted, and a wrapper that hid it would be hiding the only way that
 * fact is ever expressed. [requireValue] is for the topics that have no such thing.
 *
 * [partition] and [offset] are not decoration either — together they name the record, and they are
 * what an acknowledgement, a redelivery and a dead-letter header all refer back to.
 */
data class KafkaRecord<K, V>(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Instant,
    val key: K?,
    val value: V?,
    val headers: RecordHeaders = RecordHeaders.EMPTY,
) {
    /**
     * The assignment epoch this record was handed out under, so a completion that outlives a
     * revocation is told apart from one for the partition's new assignment. Not part of the record's
     * identity: it is not in equals, `copy` or the constructor, and a copy has none, so a completion or
     * `commit` for a `copy()`d record is accepted for the partition's current assignment, stale or not.
     */
    internal var epoch: Long = NO_EPOCH

    /** The value, or a failure naming the record — for a topic where a tombstone means nothing. */
    fun requireValue(): V = value ?: throw KafkaValueException("record has no value", topic, partition, offset)

    companion object {
        internal const val NO_EPOCH = -1L

        internal fun <K, V> from(record: ConsumerRecord<K, V>): KafkaRecord<K, V> =
            KafkaRecord(
                topic = record.topic(),
                partition = record.partition(),
                offset = record.offset(),
                timestamp = Instant.fromEpochMilliseconds(record.timestamp()),
                key = record.key(),
                value = record.value(),
                headers = RecordHeaders.from(record.headers()),
            )
    }
}
