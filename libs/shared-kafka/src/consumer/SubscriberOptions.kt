package com.strange.kafka.consumer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How a subscriber reads.
 *
 * [prefetch] is the one that is easy to read past. It is how many records may sit between the poll
 * loop and the handler, and it is what makes backpressure possible: when it fills, the loop pauses
 * its partitions and keeps polling rather than stopping. Larger smooths a bursty handler; smaller
 * means fewer records to re-handle after a crash, since nothing in that buffer has been committed.
 */
data class SubscriberOptions(
    val offsetReset: OffsetReset = OffsetReset.Latest,
    val commit: CommitStrategy = CommitStrategy.Batched(),
    val concurrency: Concurrency = Concurrency.Sequential,
    val pollTimeout: Duration = 500.milliseconds,
    val prefetch: Int = 64,
    val maxPollRecords: Int? = null,
    val properties: Map<String, String> = emptyMap(),
) {
    init {
        require(prefetch > 0) { "a subscriber buffers a positive number of records, not $prefetch" }
    }
}
