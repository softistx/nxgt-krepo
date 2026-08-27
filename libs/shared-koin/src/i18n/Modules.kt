package com.strange.koin.i18n

import com.strange.i18n.Messages
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The catalogs, for the container to hand out.
 *
 * ```kotlin
 * startKoin { modules(messagesModule(Messages.load(locales = listOf(ENGLISH, FRENCH)))) }
 *
 * class WelcomeEmail(private val messages: Messages)
 * ```
 *
 * Loaded by the caller rather than by the module: catalogs come from a classpath resource, a
 * database or a bundle, and `Messages.load` is where that choice already lives. Nothing to close.
 *
 * In an application that also serves HTTP, `install(I18n) { messages = get() }` gives its routes
 * the per-request `call.translate` over these same catalogs.
 */
fun messagesModule(messages: Messages): Module =
    module {
        single { messages }
    }
