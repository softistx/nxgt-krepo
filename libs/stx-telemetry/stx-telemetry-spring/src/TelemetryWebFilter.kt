package com.strange.telemetry.spring

import com.strange.telemetry.Telemetry
import com.strange.telemetry.context.withTelemetry
import com.strange.telemetry.continuing
import com.strange.telemetry.model.SpanKind
import com.strange.telemetry.model.SpanStatus
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.pattern.PathPattern

/**
 * One server span per request.
 *
 * A [CoWebFilter] rather than a `WebFilter`, and that is the whole design. `WebFilter` returns a
 * `Mono`, so a span opened around it would have to live in the Reactor context and be read back out
 * — and the handler that matters is a **suspending** `@RestController` method, which does not read
 * the Reactor context. `CoWebFilter` is Spring's own answer to exactly that: it runs the chain
 * inside a coroutine and hands the coroutine context on to the suspending handler, so a
 * `CoroutineContext.Element` put here is in scope there.
 *
 * That is what makes `logger<T>()` in a service class carry the request's trace with nothing passed
 * down to it — the thing an MDC cannot do under WebFlux, which the rest of this repository has
 * written down three times about the locale and the current user.
 *
 * ## Why the span is renamed at the end
 *
 * The request arrives as `GET /orders/8d1f-…`; the pattern that matched it is only known once
 * routing has run, and WebFlux publishes it as an exchange attribute. So the span starts on the raw
 * path and is renamed to `GET /orders/{id}` before it is written — a name a backend can group by. A
 * request that matched nothing keeps its path, which is what a 404 hunt wants to see.
 */
class TelemetryWebFilter(
    private val telemetry: Telemetry,
    private val ignore: List<String> = emptyList(),
) : CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain,
    ) {
        val path = exchange.request.path.value()
        if (ignore.any { path.startsWith(it) }) return chain.filter(exchange)

        withTelemetry(telemetry) {
            continuing(
                exchange.request.headers.getFirst(TRACEPARENT),
                "${exchange.request.method.name()} $path",
                "http.request.method" to exchange.request.method.name(),
                "url.path" to path,
                kind = SpanKind.Server,
            ) {
                var completed = false
                try {
                    chain.filter(exchange)
                    completed = true
                } finally {
                    exchange.pattern()?.let { pattern ->
                        name = "${exchange.request.method.name()} $pattern"
                        attribute("http.route", pattern)
                    }
                    status(exchange, completed)?.let { code ->
                        attribute("http.response.status_code", code)
                        // 5xx is this service failing; 4xx is a caller being told no, which is the
                        // service working. Colouring both red makes the dashboard useless.
                        if (code >= 500) status = SpanStatus.Error
                    }
                }
            }
        }
    }

    private companion object {
        const val TRACEPARENT = "traceparent"

        /**
         * The response's status, and why a handler that returned nothing in particular is a 200.
         *
         * WebFlux only *sets* a status when something asked for one: a `@GetMapping` returning a
         * `String` leaves `statusCode` null and the engine writes 200 at commit, which is after this
         * span is written. Reading the null as 200 is what Spring Boot's own metrics do, for the
         * same reason.
         *
         * [completed] is what keeps that from lying. A handler that threw also leaves the status
         * null, and calling *that* a 200 would be worse than recording nothing — so it records
         * nothing, and the failure on the span says what happened.
         */
        fun status(
            exchange: ServerWebExchange,
            completed: Boolean,
        ): Int? = exchange.response.statusCode?.value() ?: if (completed) 200 else null

        /** The pattern that matched, which WebFlux leaves on the exchange after routing. */
        fun ServerWebExchange.pattern(): String? =
            attributes[RouterFunctions.MATCHING_PATTERN_ATTRIBUTE]?.let { (it as? PathPattern)?.patternString }
                ?: attributes[BEST_MATCHING_PATTERN]?.let { (it as? PathPattern)?.patternString }

        /**
         * `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, spelled out.
         *
         * Naming the constant would drag `spring-webmvc`'s sibling into scope in an IDE and buys
         * nothing: the value is part of Spring's public contract and has not moved in a decade.
         */
        const val BEST_MATCHING_PATTERN = "org.springframework.web.reactive.HandlerMapping.bestMatchingPattern"
    }
}
