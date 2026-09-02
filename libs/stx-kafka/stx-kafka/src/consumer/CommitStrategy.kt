package com.softistx.kafka.consumer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * When a handled record's offset is written back to the group.
 *
 * The commit is what makes redelivery stop, so this is the dial between doing work twice and doing
 * it not at all. Everything here commits *after* the handler returns — delivery is at least once,
 * and a handler that succeeds and then loses the connection sees its record again.
 */
sealed interface CommitStrategy {
    /**
     * One commit per record. The least redelivery after a crash and the most round trips — a commit
     * is a request to the group coordinator, so this caps throughput at one record per round trip
     * unless the handler is slower than that anyway.
     */
    data object AfterEach : CommitStrategy

    /**
     * A commit every [count] records or every [every], whichever comes first. The default, because
     * it bounds redelivery by two numbers a service can reason about rather than by luck.
     */
    data class Batched(
        val count: Int = 100,
        val every: Duration = 5.seconds,
    ) : CommitStrategy {
        init {
            require(count > 0) { "a batch commits after a positive number of records, not $count" }
            require(every > Duration.ZERO) { "a batch commits after a positive interval, not $every" }
        }
    }

    /**
     * Nothing is committed for you. The caller commits, and a caller that forgets re-reads
     * everything on the next restart — which is the honest outcome, not a bug to be papered over.
     */
    data object Manual : CommitStrategy
}
