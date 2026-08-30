package com.strange.example.orders

import com.strange.example.orders.api.utils.apiErrorFilter
import com.strange.example.orders.api.utils.apiOperationProcessor
import com.strange.example.orders.api.utils.registerApiEnumConverters
import com.strange.spring.client.httpServiceFactory
import kotlinx.serialization.json.Json
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient

/**
 * The generated interfaces, as typed clients against a running instance.
 *
 * **A spec drives this application through the same interfaces its controllers implement.** That is
 * the whole claim of a spec-first API written down as a test: `OrderController` implements
 * `IOrdersService` and so does this client, neither of them wrote it, and a document change that
 * neither followed stops compiling on both sides at once. It is also why the assertions read as
 * Kotlin calls rather than as JSON paths — a `WebTestClient` proves the wire shape, and a typed
 * client proves the *contract*, which is a different claim.
 *
 * Three things have to be true for the proxy to behave, and none of them fails at build time:
 *
 * - **The `WebClient` decodes with kotlinx, not Jackson.** `models: Kotlinx` generates
 *   `@Serializable` classes whose `placedAt` is a `kotlin.time.Instant`, a type Jackson has never
 *   heard of. The application's own `stxWebJson` is reused rather than a fresh `Json`, so the client
 *   reads exactly what the server wrote.
 * - **The conversion service knows the generated enums.** Spring writes an enum argument with
 *   `Enum.name()` and never consults `toString()`, so a document spelling a value in kebab-case
 *   would go out as the Kotlin name — a request that succeeds and matches nothing.
 * - **`apiOperationProcessor` carries the operation across.** By the time a `ClientRequest` exists
 *   the method is gone, so without it `apiErrorFilter` cannot tell which operation failed and every
 *   typed failure falls back to the untyped `ApiException`.
 */
fun apiFactory(
    baseUrl: String,
    json: Json,
): HttpServiceProxyFactory =
    httpServiceFactory(
        baseUrl,
        factory = {
            conversionService(DefaultFormattingConversionService().also(::registerApiEnumConverters))
            httpRequestValuesProcessor(apiOperationProcessor())
        },
    ) {
        codecs {
            it.defaultCodecs().kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
            it.defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
        }
        // Turns a documented non-2xx into the exception the document describes. It runs closer to
        // the transport than `httpServiceFactory`'s own status handler, so what a caller catches is
        // the generated `ErrorResponseException` carrying a parsed body rather than the library's
        // untyped `ApiException` — which is what an external consumer of this API would get.
        filter(apiErrorFilter())
    }

/** One client per tag, from one factory — the shape a spec against several groups wants. */
inline fun <reified T : Any> HttpServiceProxyFactory.client(): T = createClient<T>()
