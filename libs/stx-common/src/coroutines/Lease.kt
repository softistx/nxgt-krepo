package com.softistx.common.coroutines

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * A distributed lock built out of two fields, for the stores that have no lock to borrow.
 *
 * Redis has `SET NX PX` and a Lua release. Neither a relational database nor MongoDB has anything of
 * the kind, so both do the same thing instead: an owner and an expiry that one conditional write
 * sets, another extends and a third clears. **Only those three writes differ between stores** — the
 * policy around them is one policy, and this is it. Two libraries hold it now: `stx-workflow-db`'s
 * relational and Mongo stores, whose `guarded` is *one worker advances this instance*, and
 * `stx-migrations-db`'s two ledgers, whose lock is *one process runs the migrations*.
 *
 * ```kotlin
 * private val guard = Lease(duration, ::take, ::renew, ::release)
 *
 * suspend fun <T> guarded(id: String, block: suspend () -> T): T? = guard.guard(id, block)
 * ```
 *
 * - **[take] declines, it does not queue.** Whoever holds the id is already working on it, and
 *   queueing behind them ends with a whole fleet parked on one slow step. A caller that must wait
 *   rather than give up polls this instead — that is a decision about the caller, and it stays with
 *   the caller.
 * - **It renews while the work runs**, a third of [duration] at a time, because the lease says
 *   *this is being worked on right now*, not *this code is short*. That is what separates it from a
 *   fixed expiry, which has to be guessed against the slowest run and is wrong either way.
 * - **[release] runs even when the coroutine is cancelled.** A cancelled coroutine cannot make a
 *   suspending call, and a scope dying mid-step is exactly what a crash looks like — without
 *   [NonCancellable] the release would throw out of the `finally` and leave the id held until the
 *   lease expired on its own.
 *
 * The three callbacks all suspend, which is why this is in `coroutines/` and not `concurrent/`:
 * every one of them is a write to a database.
 */
class Lease(
    private val duration: Duration,
    private val take: suspend (String) -> Boolean,
    private val renew: suspend (String) -> Boolean,
    private val release: suspend (String) -> Unit,
) {
    /**
     * Runs [block] holding the lease on [id], and answers null without running it when somebody else
     * holds it.
     *
     * Null is the whole signal: it is not an error, it is *somebody is already doing this*.
     */
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
