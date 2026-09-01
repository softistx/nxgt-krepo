package com.softistx.ktor.i18n

import com.softistx.i18n.Messages
import com.softistx.i18n.Translator
import com.softistx.ktor.required
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall

/** The catalogs [I18n] was installed with. Per-application, unlike [translator], which is per-request. */
val Application.messages: Messages get() = required(MessagesKey, "I18n")

/**
 * The translator for this request, as [I18n] resolved it.
 *
 * Throws when the plugin is not installed, rather than quietly answering in English — a service
 * whose translations silently stopped negotiating is worse off than one that fails on the first
 * request after the mistake.
 */
val ApplicationCall.translator: Translator
    get() =
        attributes.getOrNull(TranslatorKey)
            ?: application.required(TranslatorKey, "I18n")

/** The message for [key] in this request's locale. */
fun ApplicationCall.translate(
    key: String,
    vararg args: Any,
): String = translator.translate(key, *args)

/** The message for [key] in this request's locale, with named arguments. */
fun ApplicationCall.translate(
    key: String,
    args: Map<String, Any>,
): String = translator.translate(key, args)
