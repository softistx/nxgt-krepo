package com.softistx.jpa.ktor

import com.softistx.jpa.Jpa
import com.softistx.ktor.required
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The application's session factory, as [JpaConnection] built it. */
val Application.jpa: Jpa get() = required(JpaKey, "JpaConnection")

/**
 * The same factory, from a route.
 *
 * Shared, and safe to be: a factory is thread-safe and a session is not. The session a
 * `transaction { }` block opens belongs to that block and to the Vert.x context it runs on — it must
 * not be stored, passed to another coroutine, or awaited outside the block.
 */
val ApplicationCall.jpa: Jpa get() = application.jpa
