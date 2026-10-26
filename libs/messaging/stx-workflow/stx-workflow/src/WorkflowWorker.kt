package com.softistx.workflow

import com.softistx.common.lifecycle.CloseGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
    /**
     * How long an id whose definition is not registered here is left alone.
     *
     * Another deployment may own it, so it is neither failed nor retried every poll: the store keeps
     * offering it, and without this the loop would ask for it again as fast as the store answers.
     */
    private val unknownBackoff: Duration = 1.minutes,
    /**
     * Told about every `resume` that threw, with the instance's id. The default writes a warning
     * to the JDK's `System.Logger`; nothing is swallowed. An id that failed is left alone for one
     * [poll] before it is offered again.
     */
    private val onError: (id: String, cause: Throwable) -> Unit = { id, cause ->
        System.getLogger(WorkflowWorker::class.java.name).log(System.Logger.Level.WARNING, "resuming workflow '$id' failed", cause)
    },
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
        val inFlight = ConcurrentHashMap.newKeySet<String>()
        val parked = ConcurrentHashMap<String, Instant>()
        return scope
            .launch {
                while (currentCoroutineContext().isActive) {
                    val now = Clock.System.now()
                    parked.values.removeIf { it <= now }
                    var started = 0
                    // Parked and in-flight ids still sit in the store's window, so ask for enough to see past them.
                    for (id in engine.runnable(now, batch + parked.size + inFlight.size)) {
                        if (id in inFlight || parked.containsKey(id)) continue
                        // The permit is taken here, before `launch`, so a backlog waits in the loop
                        // instead of becoming a coroutine apiece. Taken inside the child it would
                        // still bound the work but not the number of coroutines parked waiting.
                        limit.acquire()
                        inFlight.add(id)
                        started++
                        launch {
                            try {
                                engine.resume(id)
                                // `resume` returns quietly when another engine holds the lock, and one that
                                // made progress ran until it blocked: either way the id waits a poll.
                                parked[id] = Clock.System.now() + poll
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: WorkflowUnknownException) {
                                parked[id] = Clock.System.now() + unknownBackoff
                                report(id, e)
                            } catch (e: Throwable) {
                                parked[id] = Clock.System.now() + poll
                                report(id, e)
                            } finally {
                                inFlight.remove(id)
                                limit.release()
                            }
                        }
                    }
                    // Suspends either way. `delay(ZERO)` returns without suspending, so the busy
                    // path needs a yield of its own rather than a duration of zero. When no id in
                    // the batch was started (all in flight, parked, or none due) nothing made
                    // progress, and the next look waits a poll.
                    if (started == 0) delay(poll) else yield()
                }
            }.also { job = it }
    }

    private fun report(
        id: String,
        cause: Throwable,
    ) {
        // A hook that throws must not take the child down with it and leak nothing: the finally
        // above still runs, and the failure is the hook's own to own.
        runCatching { onError(id, cause) }
    }

    /** Stops claiming. It does not close the engine or the connection under it — it opened neither. */
    override fun close() = guard.once { job?.cancel() }
}
