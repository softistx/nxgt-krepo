package com.softistx.telemetry.ktor

import com.softistx.telemetry.Telemetry
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Registers the telemetry the plugin built with Ktor's DI, without building a second one.
 *
 * ```kotlin
 * install(Observability) { service = "checkout" }
 *
 * class Checkouts(private val telemetry: Telemetry)   // no ApplicationCall in sight
 * ```
 *
 * Called by [Observability] at install — there is nothing to switch on.
 *
 * Most classes need none of this: `logger<T>()` and `span { }` find the installed telemetry on their
 * own, which is what `install = true` is for. This is for the rare one that wants the root itself —
 * to read its resource, or to close it deliberately.
 *
 * The container will close what it hands out, and this hands out something the plugin may already
 * own. `Telemetry.close` is idempotent for exactly that reason.
 */
internal fun Application.provideTelemetry() {
    val telemetry = telemetry
    dependencies {
        provide<Telemetry> { telemetry }
    }
}
