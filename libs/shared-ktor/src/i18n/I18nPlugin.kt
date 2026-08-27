package com.strange.ktor.i18n

import com.strange.i18n.Messages
import com.strange.i18n.Translator
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.request.ApplicationRequest
import io.ktor.util.AttributeKey

/**
 * Resolves each request's locale once, and hands it to the route.
 *
 * ```kotlin
 * install(I18nPlugin) { messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)) }
 *
 * get("/greeting") {
 *     call.respondText(call.translate("hello.world", mapOf("name" to "Ada")))
 * }
 * ```
 *
 * A separate module from `shared-i18n` so that the catalogs do not drag Ktor in behind them: a
 * worker, a CLI or a Kafka consumer translates the same messages and has no server in it.
 *
 * **Resolved once per call**, at the start, and kept on the call. Negotiating in each handler that
 * needs a message would parse the same header several times and — worse — could answer two
 * questions in one response in two different languages.
 */
val I18nPlugin =
    createApplicationPlugin(name = "I18n", createConfiguration = ::I18nConfiguration) {
        val messages = requireNotNull(pluginConfig.messages) { "install(I18nPlugin) needs `messages`" }
        val configuration = pluginConfig.copy()

        on(CallSetup) { call ->
            call.attributes.put(TranslatorKey, messages.forRequest(call.request, configuration))
        }
    }

/** How [I18nPlugin] decides which locale a request is asking for. */
data class I18nConfiguration(
    /** The catalogs. Required — there is no sensible default for somebody else's messages. */
    var messages: Messages? = null,
    /** The header to read. `Accept-Language` unless something in front of this rewrites it. */
    var header: String = HttpHeaders.AcceptLanguage,
    /**
     * A query parameter that overrides the header — `?lang=fr`.
     *
     * Off by default, and deliberately. It is genuinely useful for a link somebody sends a
     * colleague, and it is also a second thing the same URL can mean, which every cache in front of
     * the service has to be told about. Turn it on when you have decided that.
     */
    var queryParameter: String? = null,
)

internal val TranslatorKey = AttributeKey<Translator>("com.strange.i18n.Translator")

private fun Messages.forRequest(
    request: ApplicationRequest,
    configuration: I18nConfiguration,
): Translator {
    val asked = configuration.queryParameter?.let { request.queryParameters[it] }
    return negotiate(asked ?: request.headers[configuration.header])
}
