package com.strange.kafka.consumer

/**
 * How many records a consumer handles at once.
 *
 * The reason this is not just a number: Kafka's only ordering guarantee is *within a partition*, so
 * the safe unit of parallelism is the partition, not the record. Handling two records from one
 * partition concurrently reorders them, and on a per-entity change feed that means an older state
 * can overwrite a newer one.
 */
sealed interface Concurrency {
    /** One record at a time, in the order they were read. The safe default. */
    data object Sequential : Concurrency

    /**
     * One handler per assigned partition, running concurrently, each partition still in order.
     *
     * The offsets committed are the contiguous completed prefix per partition: if records 5 and 7
     * finish while 6 is still running, the commit stops at 6, because committing past a record
     * still in flight would lose it on a crash.
     */
    data class PerPartition(
        val limit: Int = 16,
    ) : Concurrency {
        init {
            require(limit > 0) { "a positive number of partitions can be in flight, not $limit" }
        }
    }
}
