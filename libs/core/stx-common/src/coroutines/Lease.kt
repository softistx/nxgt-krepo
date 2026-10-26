package com.softistx.common.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.TimeSource

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
 *   A renewal that **throws** is reported to [onRenewalFailure] and retried on the next tick, so a
 *   dropped connection does not end the work; but once no renewal has **succeeded** within [duration]
 *   the lease has lapsed on the store, and the block is cancelled with a [LeaseLostException] —
 *   failing closed, because a second holder may already be running. One that answers false means the
 *   lease was lost, and the watchdog stops (the block is not interrupted).
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
    private val onRenewalFailure: (String, Throwable) -> Unit = { id, e ->
        LOG.log(System.Logger.Level.WARNING, "lease '$id' could not be renewed", e)
    },
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
                var lastRenewed = TimeSource.Monotonic.markNow()
                val watchdog =
                    launch {
                        while (isActive) {
                            delay(duration / 3)
                            // A renewal that throws (a dropped connection) is reported and retried on the next
                            // tick; the lease only lapses if none succeeds within `duration`, and then the
                            // block is cancelled. Cancellation is the exception to the exception.
                            val renewed =
                                try {
                                    renew(id).also { if (it) lastRenewed = TimeSource.Monotonic.markNow() }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    onRenewalFailure(id, e)
                                    if (lastRenewed.elapsedNow() >= duration) {
                                        throw LeaseLostException(id, duration, e)
                                    }
                                    true
                                }
                            if (!renewed) break
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

private val LOG = System.getLogger(Lease::class.java.name)

/** The lease could not be renewed for a whole duration, so the block holding it was cancelled. */
class LeaseLostException(
    id: String,
    duration: Duration,
    cause: Throwable,
) : IllegalStateException("lease '$id' was not renewed within $duration and is presumed lost", cause)
