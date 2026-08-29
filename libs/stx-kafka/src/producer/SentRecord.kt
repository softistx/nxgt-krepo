package com.strange.kafka.producer

import org.apache.kafka.clients.producer.RecordMetadata
import kotlin.time.Instant

/**
 * Where a record landed.
 *
 * The answer to a send is not "ok" — it is a position in the log, and a caller that keeps it can say
 * later exactly what it wrote and where. That is what makes a produce auditable and what a
 * transactional pipeline records as its output.
 */
data class SentRecord(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Instant,
) {
    companion object {
        internal fun from(metadata: RecordMetadata): SentRecord =
            SentRecord(
                topic = metadata.topic(),
                partition = metadata.partition(),
                offset = metadata.offset(),
                timestamp = Instant.fromEpochMilliseconds(metadata.timestamp()),
            )
    }
}
