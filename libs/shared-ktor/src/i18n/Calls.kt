package com.strange.ktor.i18n

import com.strange.i18n.Translator
import io.ktor.server.application.ApplicationCall

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
            ?: error("the I18n plugin is not installed — call install(I18n) { messages = … } first")

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
