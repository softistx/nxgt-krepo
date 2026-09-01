package com.strange.workflow.spring

import com.strange.workflow.WorkflowWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.springframework.context.SmartLifecycle
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The defaults `stx-workflow` and `stx-workflow-db` carry, restated where the binder can reach them. */
internal val WORKER_POLL = 1.seconds
internal val LEASE = 30.seconds
internal val RETENTION = 7.days
internal val CHILD_POLL = 1.minutes

/**
 * Runs a [WorkflowWorker] for as long as the application context is running.
 *
 * The scope is this bean's own, and it is cancelled in [stop]. A worker started on a scope the
 * application shares would keep running while the context tore down around it, resuming instances
 * against collaborators that were already closing — which surfaces as a handful of failures at
 * shutdown that look like the store's fault.
 *
 * [isAutoStartup] is true because the bean only exists when `stx.workflow.worker.enabled` is,
 * which is the decision already made.
 */
class WorkflowWorkerLifecycle internal constructor(
    private val worker: WorkflowWorker,
) : SmartLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running = false

    override fun start() {
        if (running) return
        worker.start(scope)
        running = true
    }

    override fun stop() {
        if (!running) return
        running = false
        worker.close()
        scope.cancel()
    }

    override fun isRunning(): Boolean = running
}
