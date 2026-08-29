package com.strange.jpa.session

import io.vertx.core.Vertx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Runs [block] as a coroutine that never leaves the Vert.x context it started on.
 *
 * **This is the one thing this library exists to get right.** A Hibernate Reactive session belongs
 * to the context that opened it — *"You're only allowed to use the session from the thread that owns
 * this local context"* — and an ordinary coroutine resumes wherever its dispatcher decides. Suspend
 * once inside a session block and touch the session afterwards, and Hibernate refuses it:
 *
 * ```
 * HR000069: Detected use of the reactive Session from a different Thread than the one which was used
 * to open the reactive Session - this suggests an invalid integration
 * ```
 *
 * `SessionConfinementTest` shows both halves — the naive bridge failing, and this one keeping the
 * thread across a real suspension — because a rule with no failing case beside it reads like
 * superstition to whoever touches this next.
 *
 * The dispatcher posts every resumption back through `runOnContext`, and the context is read **here,
 * inside Hibernate's own callback**, rather than from a `Vertx` handle: that is the context the
 * session was just associated with, and `Vertx.getOrCreateContext()` is not guaranteed to be it.
 *
 * [caller] is the calling coroutine's context, minus its dispatcher, so cancelling the caller
 * cancels the work inside the session instead of leaving it running against a transaction nobody is
 * waiting for any more.
 *
 * **The job is a supervisor, and that is not a detail.** A plain child job would carry a failure
 * *upward* — the block throws, and the coroutine awaiting the transaction is cancelled by its own
 * child before Hibernate has been told anything. The failure has to travel the other way: out
 * through this stage, into `withTransaction`, which rolls back and completes the stage
 * exceptionally, so the caller sees the original exception from `await()` and the transaction is
 * undone. `SessionsTest` pins it — without the supervisor, the rollback scenario fails with the
 * exception escaping the caller's `shouldThrow`.
 */
internal fun <T> confined(
    caller: CoroutineContext,
    block: suspend CoroutineScope.() -> T,
): CompletionStage<T> {
    val context =
        requireNotNull(Vertx.currentContext()) {
            "no Vert.x context here — this runs inside Hibernate Reactive's own callback, and that is the only place it works"
        }
    val dispatcher = Executor { task -> context.runOnContext { task.run() } }.asCoroutineDispatcher()
    val job = SupervisorJob(caller[Job])

    return CoroutineScope(caller + job + dispatcher)
        .future { block() }
        // A supervisor does not finish on its own, and an unfinished child would keep the caller's
        // job from ever completing.
        .whenComplete { _, _ -> job.complete() }
}

/**
 * The calling coroutine's context with its dispatcher taken out, which is what [confined] wants.
 *
 * Everything else is worth keeping — the job, so cancellation still reaches the work inside the
 * session; the name, so a stack trace still says which coroutine this was. The dispatcher is the
 * one part that must not survive, because it is exactly what would move a resumption off the
 * session's thread.
 */
internal suspend fun callerContext(): CoroutineContext = currentCoroutineContext().minusKey(ContinuationInterceptor)
