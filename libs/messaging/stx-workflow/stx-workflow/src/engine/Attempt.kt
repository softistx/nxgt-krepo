package com.softistx.workflow.engine

import com.softistx.workflow.NonRetryableException
import com.softistx.workflow.dsl.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/** What [attempt] produced, and how many tries it took. */
internal class Outcome<T>(
    val value: T,
    val attempts: Int,
)

/** What [attempt] throws once it has given up. [attempts] is what the journal records. */
internal class NodeFailure(
    override val cause: Throwable,
    val attempts: Int,
) : Exception(cause)

/**
 * Runs [block] until it works, until the policy runs out, or until the failure says not to bother.
 *
 * **A timeout covers one attempt.** Timing the node as a whole would mean each retry inheriting what
 * the last one had already spent, so the final attempt — the one that matters — is always the one
 * given the least time.
 *
 * The three catch clauses are in the order they are for a reason. `withTimeout` reports itself as a
 * [TimeoutCancellationException], which *is* a [CancellationException], so it has to be recognised
 * first or every timeout would look like the caller cancelling. And a real [CancellationException]
 * is rethrown untouched and never retried: it means this process is going away — a shutdown, a
 * cancelled scope — and the correct thing to do is to leave the instance exactly as it is, with
 * nothing written, for whoever resumes it next. That path is the crash, and treating it as a step
 * failure would compensate a workflow that was only interrupted.
 */
internal suspend fun <T> attempt(
    policy: RetryPolicy,
    timeout: Duration?,
    block: suspend (attempt: Int) -> T,
): Outcome<T> {
    var n = 1
    while (true) {
        try {
            val value = if (timeout == null) block(n) else withTimeout(timeout) { block(n) }
            return Outcome(value, n)
        } catch (e: TimeoutCancellationException) {
            if (n >= policy.times) throw NodeFailure(e, n)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e is NonRetryableException || !policy.retryable(e) || n >= policy.times) throw NodeFailure(e, n)
        }
        delay(policy.backoff.before(n + 1))
        n++
    }
}
