package com.softistx.workflow.ktor

import com.softistx.ktor.own
import com.softistx.ktor.publish
import com.softistx.workflow.Workflow
import com.softistx.workflow.WorkflowEngine
import com.softistx.workflow.WorkflowEngineBuilder
import com.softistx.workflow.WorkflowWorker
import com.softistx.workflow.store.WorkflowStore
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One workflow engine for the application, and optionally the worker that keeps it honest.
 *
 * ```kotlin
 * install(RedisConnection) { config = RedisConfig(uri = …, namespace = "orders") }
 * install(Workflows) {
 *     store = RedisWorkflowStore(application.redis)
 *     register(checkout)
 *     worker = true
 * }
 *
 * post("/checkout") { call.respond(call.workflows.start(checkout, call.receive())) }
 * ```
 *
 * The engine is not a resource — it owns neither the store nor the connection under it, and has
 * nothing to close. The **worker** is, and it is the reason this plugin exists rather than a line of
 * application code: a worker started on the application's own scope and never stopped is a
 * background loop that outlives the redeploy it was supposed to die with, resuming instances against
 * a store the next process is also resuming them against.
 *
 * [worker] is off by default. Installing this plugin gives an application a way to *run* workflows;
 * it should not also, silently, enlist it in recovering everybody else's — that is a decision about
 * how a fleet is shaped, and it belongs to whoever is shaping it.
 */
val Workflows =
    createApplicationPlugin(name = "Workflows", createConfiguration = ::WorkflowsConfiguration) {
        val engine =
            pluginConfig.instance ?: WorkflowEngine(
                requireNotNull(pluginConfig.store) { "install(Workflows) needs a store, or an engine as instance" },
            ) {
                pluginConfig.registrations.forEach(::register)
                pluginConfig.engineBlock?.invoke(this)
            }
        application.publish(EngineKey, engine)
        if (pluginConfig.worker) {
            val worker =
                application.own(
                    WorkerKey,
                    WorkflowWorker(engine, pluginConfig.poll, pluginConfig.batch, pluginConfig.concurrency),
                )
            // The application is the scope, so the loop is cancelled when it winds down even if the
            // close on ApplicationStopped never runs — a process killed rather than stopped.
            worker.start(application)
        }
        if (pluginConfig.injectable) application.provideWorkflows()
    }

/** What [Workflows] runs with. Either [store] or [instance] must be set. */
class WorkflowsConfiguration {
    /**
     * Where instances live.
     *
     * Ignored when [instance] is set. This plugin never opens the connection under a store: a
     * `RedisWorkflowStore(application.redis)` shares the one `install(RedisConnection)` opened, and
     * a second connection for the same server is a pool nobody asked for.
     */
    var store: WorkflowStore? = null

    /**
     * An engine built elsewhere — by a DI container, or by hand.
     *
     * When set, [store] and the registrations are ignored. There is nothing to close either way: an
     * engine holds no resource of its own.
     */
    var instance: WorkflowEngine? = null

    /**
     * Runs a [WorkflowWorker] on the application's scope, stopped with the application.
     *
     * Off by default. See the plugin's own note: whether this process is one that recovers abandoned
     * instances is a decision about the fleet, not a default.
     */
    var worker: Boolean = false

    /** How long the worker waits between two looks at an empty index. */
    var poll: Duration = 1.seconds

    /** How many due instances the worker takes at a time. */
    var batch: Int = 32

    /** How many instances the worker advances at once. */
    var concurrency: Int = 8

    /**
     * Registers the engine with Ktor's DI as well, so a class the container builds can take a
     * [WorkflowEngine] in its constructor — the same one `call.workflows` hands a route.
     *
     * Off by default, and it has to be: `ktor-server-di` is compile-only in this module, so an
     * application that never asks for this must not be made to carry it at runtime.
     */
    var injectable: Boolean = false

    internal val registrations = mutableListOf<Workflow<*>>()

    /**
     * A workflow this engine can run.
     *
     * Registration is not optional bookkeeping: an instance is stored under its workflow's *name*,
     * and an engine that cannot look that name up cannot resume it after a restart. A worker in a
     * process that registered half the fleet's workflows will fail on the other half.
     */
    fun register(workflow: Workflow<*>) {
        registrations += workflow
    }

    /** [register], for a call site that already has a list. */
    fun register(workflows: Iterable<Workflow<*>>) {
        registrations += workflows
    }

    /** Extra [WorkflowEngineBuilder] configuration — the `Json` a context is written and read with. */
    fun engine(block: WorkflowEngineBuilder.() -> Unit) {
        engineBlock = block
    }

    internal var engineBlock: (WorkflowEngineBuilder.() -> Unit)? = null
}

internal val EngineKey = AttributeKey<WorkflowEngine>("com.softistx.workflow.WorkflowEngine")
internal val WorkerKey = AttributeKey<WorkflowWorker>("com.softistx.workflow.WorkflowWorker")
