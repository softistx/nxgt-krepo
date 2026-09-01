package com.strange.workflow

import com.strange.common.lifecycle.CloseGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Picks up instances nobody is advancing.
 *
 * ```kotlin
 * WorkflowWorker(engine).use { worker ->
 *     worker.start(applicationScope)
 * }
 * ```
 *
 * **Nothing starts one.** Putting this module on a classpath starts no background work; an
 * application that drives `resume` from a scheduler of its own never constructs this class. That is
 * the opposite of the usual "helpful" default, and it is deliberate: a worker that appeared on its
 * own would be a second thing advancing instances in a process that thought it had one.
 *
 * It knows nothing about any particular store. It asks the engine what is due and resumes it, which
 * is `WorkflowStore.runnable` and `WorkflowStore.guarded` and nothing else — so the same worker
 * drives instances in Redis, in Postgres, in MongoDB or in memory. That is why it lives here rather
 * than beside a store.
 *
 * **There is no claim step.** The worker asks the store what is due and calls `resume` on each; the
 * engine takes the instance's lock itself and returns quietly when somebody else has it. Two workers
 * pulling the same id is the normal shape of this, not a race to prevent — the lock decides, and the
 * loser has already moved on to the next id by the time it matters.
 */
class WorkflowWorker(
    private val engine: WorkflowEngine,
    /** How long between two looks at the index, when the last one found nothing. */
    private val poll: Duration = 1.seconds,
    /** How many ids to take from the index at a time. */
    private val batch: Int = 32,
    /** How many instances this worker advances at once. */
    private val concurrency: Int = 8,
) : AutoCloseable {
    private val guard = CloseGuard()
    private var job: Job? = null

    /**
     * Starts claiming on [scope], and hands back the job so a caller can join it.
     *
     * The scope belongs to the caller — an application's own, a Ktor `ApplicationScope`, whatever
     * cancels when the process winds down. This class starts a coroutine, never a thread.
     */
    fun start(scope: CoroutineScope): Job {
        check(job == null) { "this worker has already been started" }
        val limit = Semaphore(concurrency)
        return scope
            .launch {
                while (currentCoroutineContext().isActive) {
                    val ids = engine.runnable(Clock.System.now(), batch)
                    // The permit is taken inside the coroutine, not around starting it — around it,
                    // it would be released the instant `launch` returned and would bound nothing.
                    ids.forEach { id -> launch { limit.withPermit { runCatching { engine.resume(id) } } } }
                    // Suspends either way. `delay(ZERO)` returns without suspending, so the busy
                    // path needs a yield of its own rather than a duration of zero.
                    if (ids.isEmpty()) delay(poll) else yield()
                }
            }.also { job = it }
    }

    /** Stops claiming. It does not close the engine or the connection under it — it opened neither. */
    override fun close() = guard.once { job?.cancel() }
}
