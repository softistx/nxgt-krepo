package com.softistx.i18n.ktor

import com.softistx.i18n.Messages
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

/**
 * Registers the catalogs the plugin installed with Ktor's DI, without loading them a second time.
 *
 * ```kotlin
 * install(I18n) { messages = Messages.load(…) }
 *
 * class WelcomeEmail(private val messages: Messages)   // built by the container, no ApplicationCall in sight
 * ```
 *
 * Called by [I18n] at install — there is nothing to switch on.
 *
 * `Messages` holds no connection and is not `AutoCloseable`, so the container has nothing to close
 * and the application owns the catalogs for as long as it runs.
 */
internal fun Application.provideMessages() {
    val catalogs = messages
    dependencies {
        provide<Messages> { catalogs }
    }
}
