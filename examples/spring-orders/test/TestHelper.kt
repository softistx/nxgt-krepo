package com.strange.example.orders

import com.strange.example.orders.api.utils.apiErrorFilter
import com.strange.example.orders.api.utils.apiOperationProcessor
import com.strange.example.orders.api.utils.registerApiEnumConverters
import com.strange.spring.client.httpServiceFactory
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.json.Json
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import kotlin.time.Duration.Companion.seconds

/** Whether a spec that needs MongoDB can run — every feature below is `.config(enabled = …)` on it. */
val mongoAvailable: Boolean get() = OrdersSpec.mongo.available

/**
 * The proxy factory the generated clients need, from `stx-spring-boot`'s own [httpServiceFactory].
 *
 * The client extensions are the library's, not this module's: a spec that assembled its own
 * `WebClient` would be asserting against a transport no caller uses. What is added here is only what
 * a *generated* client needs on top, and none of the four fails at build time:
 *
 * - **The `WebClient` decodes with kotlinx, not Jackson.** `models: Kotlinx` generates
 *   `@Serializable` classes whose `placedAt` is a `kotlin.time.Instant`, a type Jackson has never
 *   heard of. The application's own `stxWebJson` bean is injected into the spec and passed here, so
 *   the client reads exactly what the server wrote.
 * - **The conversion service knows the generated enums.** Spring writes an enum argument with
 *   `Enum.name()` and never consults `toString()`, so a document spelling a value in kebab-case
 *   would go out as the Kotlin name — a request that succeeds and matches nothing.
 * - **`apiOperationProcessor` carries the operation across.** By the time a `ClientRequest` exists
 *   the method is gone, so without it `apiErrorFilter` cannot tell which operation failed and every
 *   typed failure falls back to the untyped `ApiException`.
 * - **`apiErrorFilter` turns a documented non-2xx into the exception the document describes.** It
 *   runs closer to the transport than `httpServiceFactory`'s own status handler, so what a caller
 *   catches is the generated `ErrorResponseException` carrying a parsed body.
 *
 * [headers] runs per request, which is where an API with authentication puts its token —
 * `apiFactory(json) { it.setBearerAuth(token) }`, the seam `nxgt-rest`'s specs use. This one
 * authenticates nobody, so it is empty here and present anyway, because it is the parameter a real
 * API's spec reaches for first.
 */
fun apiFactory(
    json: Json,
    baseUrl: String = OrdersSpec.BASE_URL,
    headers: (HttpHeaders) -> Unit = {},
): HttpServiceProxyFactory =
    httpServiceFactory(
        baseUrl,
        headers,
        factory = {
            conversionService(DefaultFormattingConversionService().also(::registerApiEnumConverters))
            httpRequestValuesProcessor(apiOperationProcessor())
        },
    ) {
        codecs {
            it.defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
            it.defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
        }
        filter(apiErrorFilter())
    }

/**
 * For what a typed client cannot say: the envelope's JSON shape, a status the document never declared.
 *
 * `bindToServer` and not `bindToApplicationContext`, which is what `nxgt-rest`'s helper does: the
 * kotlinx codecs, the exception advice, the locale negotiation and the `kotlin.time.Instant`
 * converters are auto-configurations, and a client bound to a context bypasses several of them.
 */
fun webTestClient(baseUrl: String = OrdersSpec.BASE_URL): WebTestClient = WebTestClient.bindToServer().baseUrl(baseUrl).build()

/**
 * Empties what a scenario writes, so each one starts from a state it can name.
 *
 * The `migrations` collection is deliberately left alone: it is what records that `V1Seed` and
 * `V2Tags` ran, the context is shared across these specs, and a migration does not run twice.
 */
suspend fun ReactiveMongoTemplate.clean() {
    remove(Query(), "orders").awaitSingle()
    remove(Query(), "audits").awaitSingle()
}

/**
 * Suspends until the migrations have run.
 *
 * `MigrationRunner` listens for `ApplicationReadyEvent` and suspends, and Spring does not wait for a
 * suspending listener — so the seed is still being written while the port is already open. Without
 * this gate the seeded orders would land in the middle of whichever scenario happened to go first,
 * after its [clean]. A migration is not a startup gate, and a spec that assumes it is will be flaky
 * on a fast machine and green on a slow one.
 */
suspend fun ReactiveMongoTemplate.awaitMigrations() {
    eventually(10.seconds) {
        count(Query(), "migrations").awaitSingle() shouldBe 2L
    }
}
