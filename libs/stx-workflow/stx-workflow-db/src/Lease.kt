package com.strange.workflow.db

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * `WorkflowStore.guarded`, for the stores that have no lock to borrow.
 *
 * Redis has `RedisLock`. Neither a relational database nor MongoDB has anything of the kind, so both
 * do the same thing instead: two fields, an owner and an expiry, that one conditional write sets and
 * another clears. Only the three writes differ between them — the policy around those writes is one
 * policy, and this is it.
 *
 * - **[take] declines, it does not queue.** Whoever holds the instance is already advancing it;
 *   queueing behind them ends with every worker in a fleet parked on the same slow step.
 * - **It renews while the work runs**, a third of [duration] at a time, because the lease says
 *   *this instance is being advanced right now*, not *this code is short*.
 * - **[release] runs even when the coroutine is cancelled.** A cancelled coroutine cannot make a
 *   suspending call, and a scope dying mid-step is exactly what a crash looks like — without
 *   [NonCancellable] the release would throw out of the `finally` and leave the instance held until
 *   the lease expired on its own.
 */
internal class Lease(
    private val duration: Duration,
    private val take: suspend (String) -> Boolean,
    private val renew: suspend (String) -> Boolean,
    private val release: suspend (String) -> Unit,
) {
    suspend fun <T> guard(
        id: String,
        block: suspend () -> T,
    ): T? {
        if (!take(id)) return null
        return try {
            coroutineScope {
                val watchdog =
                    launch {
                        while (isActive) {
                            delay(duration / 3)
                            if (!renew(id)) break
                        }
                    }
                try {
                    block()
                } finally {
                    watchdog.cancel()
                }
            }
        } finally {
            withContext(NonCancellable) { release(id) }
        }
    }
}
