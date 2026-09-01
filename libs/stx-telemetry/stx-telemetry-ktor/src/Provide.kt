package com.softistx.telemetry.ktor

import com.softistx.telemetry.Telemetry
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the telemetry the plugin built injectable, without building a second one.
 *
 * ```kotlin
 * install(Observability) { service = "checkout"; injectable = true }
 *
 * class Checkouts(private val telemetry: Telemetry)   // no ApplicationCall in sight
 * ```
 *
 * Most classes need none of this: `logger<T>()` and `span { }` find the installed telemetry on their
 * own, which is what `install = true` is for. This is for the rare one that wants the root itself —
 * to read its resource, or to close it deliberately.
 *
 * The container will close what it hands out, and this hands out something the plugin may already
 * own. `Telemetry.close` is idempotent for exactly that reason.
 */
fun Application.provideTelemetry() {
    val telemetry = telemetry
    dependencies {
        provide<Telemetry> { telemetry }
    }
}
