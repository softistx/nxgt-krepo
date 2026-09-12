package com.softistx.workflow.ktor

import com.softistx.workflow.WorkflowEngine
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Registers the engine the plugin built with Ktor's DI, without building a second one.
 *
 * ```kotlin
 * install(Workflows) { store = RedisWorkflowStore(application.redis); register(checkout) }
 *
 * class Checkouts(private val workflows: WorkflowEngine)   // no ApplicationCall in sight
 * ```
 *
 * Called by [Workflows] at install — there is nothing to switch on.
 *
 * Unlike the connection plugins in this module there is nothing here for the container to close —
 * a `WorkflowEngine` is not `AutoCloseable`, because it owns neither the store nor the connection
 * beneath it. The double-close question those plugins have to answer does not arise.
 */
internal fun Application.provideWorkflows() {
    val engine = workflows
    dependencies {
        provide<WorkflowEngine> { engine }
    }
}
