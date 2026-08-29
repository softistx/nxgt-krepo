package com.strange.spring.i18n

import com.strange.i18n.Messages
import com.strange.i18n.Translator
import org.springframework.web.server.ServerWebExchange

/**
 * The catalogs bound to the locale of the request being handled.
 *
 * ```kotlin
 * @GetMapping("/orders")
 * suspend fun list(exchange: ServerWebExchange) = messages.forRequest(exchange)["orders.title"]
 * ```
 *
 * **The locale comes from the exchange, not from `LocaleContextHolder`.** That holder is a
 * `ThreadLocal`, and WebFlux is the one Spring stack where a request is not a thread: a handler can
 * resume on a different worker after any suspension point, and whatever the holder then returns
 * belongs to whichever request last ran there. It usually looks right in development, where one
 * request is in flight at a time, and starts serving French to English users under load — a bug
 * with no stack trace and no failing test. The exchange follows the request wherever it resumes.
 *
 * Which locale the exchange carries is Spring's `LocaleContextResolver` decision, and by default
 * that is the `Accept-Language` header. `Messages.negotiate` is the alternative when the header is
 * in hand directly rather than through an exchange.
 */
fun Messages.forRequest(exchange: ServerWebExchange): Translator = forLocale(exchange.localeContext.locale ?: fallback)
