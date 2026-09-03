package com.softistx.i18n.ktor

import com.softistx.i18n.Messages
import com.softistx.i18n.Translator
import com.softistx.ktor.publish
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.util.*

/**
 * Resolves each request's locale once, and hands it to the route.
 *
 * ```kotlin
 * install(I18n) { messages = Messages.load(locales = listOf(Locale.ENGLISH, Locale.FRENCH)) }
 *
 * get("/greeting") {
 *     call.respondText(call.translate("hello.world", mapOf("name" to "Ada")))
 * }
 * ```
 *
 * A separate module from `stx-i18n` so that the catalogs do not drag Ktor in behind them: a
 * worker, a CLI or a Kafka consumer translates the same messages and has no server in it.
 *
 * **Resolved once per call**, at the start, and kept on the call. Negotiating in each handler that
 * needs a message would parse the same header several times and — worse — could answer two
 * questions in one response in two different languages.
 *
 * **Installing it registers the catalogs with Ktor's DI**, so a class the container builds — an
 * email renderer, a report job — takes a [Messages] in its constructor. A route keeps using
 * `call.translate`, which is per-request and is not what DI is for.
 */
val I18n =
    createApplicationPlugin(name = "I18n", createConfiguration = ::I18nConfiguration) {
        val messages = requireNotNull(pluginConfig.messages) { "install(I18n) needs `messages`" }
        val configuration = pluginConfig.copy()

        application.publish(MessagesKey, messages)
        application.provideMessages()

        on(CallSetup) { call ->
            call.attributes.put(TranslatorKey, messages.forRequest(call.request, configuration))
        }
    }

/** How [I18n] decides which locale a request is asking for. */
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

internal val TranslatorKey = AttributeKey<Translator>("com.softistx.i18n.Translator")
internal val MessagesKey = AttributeKey<Messages>("com.softistx.i18n.Messages")

private fun Messages.forRequest(
    request: ApplicationRequest,
    configuration: I18nConfiguration,
): Translator {
    val asked = configuration.queryParameter?.let { request.queryParameters[it] }
    return negotiate(asked ?: request.headers[configuration.header])
}
