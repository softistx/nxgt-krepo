package com.softistx.i18n.ktor

import com.softistx.i18n.Messages
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Makes the catalogs the plugin installed injectable, without opening a second one.
 *
 * ```kotlin
 * install(I18n) { messages = Messages.load(…) }
 * provideMessages()
 *
 * class WelcomeEmail(private val messages: Messages)   // built by the container, no ApplicationCall in sight
 * ```
 *
 * Or in one line, which is the same thing: `install(I18n) { messages = Messages.load(…); injectable = true }`.
 *
 * **The container closes it at application stop, and that is not a problem.** Ktor's DI closes every
 * `AutoCloseable` it hands out — one a provider merely passed through included, which a spec in
 * this module pins, and a per-key `cleanup` runs in addition to that rather than instead of it. So
 * this resource is closed by the container as well as by whoever created it, and both are safe
 * because these clients close idempotently: see `CloseGuard` in `stx-common`. What it does mean
 * is that a connection which has to outlive the application should not be registered here.
 */
fun Application.provideMessages() {
    val catalogs = messages
    dependencies {
        provide<Messages> { catalogs }
    }
}
