package com.strange.workflow.ktor

import com.strange.workflow.WorkflowEngine
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the engine the plugin built injectable, without building a second one.
 *
 * ```kotlin
 * install(Workflows) { store = RedisWorkflowStore(application.redis); register(checkout) }
 * provideWorkflows()
 *
 * class Checkouts(private val workflows: WorkflowEngine)   // no ApplicationCall in sight
 * ```
 *
 * Or in one line: `install(Workflows) { …; injectable = true }`.
 *
 * Unlike the connection plugins in this module there is nothing here for the container to close —
 * a `WorkflowEngine` is not `AutoCloseable`, because it owns neither the store nor the connection
 * beneath it. The double-close question those plugins have to answer does not arise.
 */
fun Application.provideWorkflows() {
    val engine = workflows
    dependencies {
        provide<WorkflowEngine> { engine }
    }
}
