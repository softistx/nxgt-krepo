package com.softistx.workflow.ktor

import com.softistx.ktor.required
import com.softistx.workflow.WorkflowEngine
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's workflow engine, as [Workflows] built it. */
val Application.workflows: WorkflowEngine get() = required(EngineKey, "Workflows")

/**
 * The same engine, from a route.
 *
 * It is shared and holds no per-request state: an engine is a registry and a store, and the run it
 * starts belongs to the instance rather than to the call that asked for it.
 */
val ApplicationCall.workflows: WorkflowEngine get() = application.workflows
