package com.softistx.spring.client

import com.softistx.spring.error.ApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.service.annotation.GetExchange
import reactor.core.publisher.Mono
import java.util.concurrent.CopyOnWriteArrayList

private interface Catalog {
    @GetExchange("/products")
    suspend fun products(): String

    @GetExchange("/products/{grade}")
    suspend fun byGrade(
        @PathVariable grade: Grade,
    ): String
}

/** A second tag against the same upstream — what an API split across tags generates. */
private interface Warehouse {
    @GetExchange("/stock")
    suspend fun stock(): String
}

/**
 * An enum whose wire value is not its Kotlin name — the shape `plugins/openapi` generates for a
 * document whose enum is spelled in kebab-case.
 */
private enum class Grade(
    val wireValue: String,
) {
    TOP_SHELF("top-shelf"),
    ;

    override fun toString(): String = wireValue
}

/** Answers every call with [status] and [body], without a socket. */
private fun answering(
    status: HttpStatus,
    body: String,
    seen: MutableList<HttpHeaders> = CopyOnWriteArrayList(),
) = ExchangeFunction { request ->
    seen += request.headers()
    Mono.just(
        ClientResponse
            .create(status)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build(),
    )
}

class HttpClientsTest :
    StringSpec({
        "a successful call comes back as the declared type" {
            val catalog =
                httpClient<Catalog>("https://catalog.test") {
                    exchangeFunction(answering(HttpStatus.OK, "two products"))
                }

            catalog.products() shouldBe "two products"
        }

        "an upstream error arrives as an ApiException carrying its status" {
            // Not a WebClientResponseException. A failure from a service upstream and one raised
            // here have to reach a handler as the same type, or the translation gets written twice
            // — and the second time is after the first outage.
            val catalog =
                httpClient<Catalog>("https://catalog.test") {
                    exchangeFunction(
                        answering(
                            HttpStatus.NOT_FOUND,
                            """{"message":"products.not-found","code":"catalog.missing"}""",
                        ),
                    )
                }

            val failure = shouldThrow<ApiException> { catalog.products() }

            failure.status shouldBe HttpStatus.NOT_FOUND
            failure.key shouldBe "products.not-found"
            failure.code shouldBe "catalog.missing"
        }

        "an upstream that is not one of ours still fails as an ApiException" {
            // The body is read as a map rather than as ErrorResponse for this case: a decoder that
            // throws while handling an error replaces a useful 502 with a serialization failure.
            val catalog =
                httpClient<Catalog>("https://catalog.test") {
                    exchangeFunction(answering(HttpStatus.BAD_GATEWAY, "<html>upstream is unwell</html>"))
                }

            val failure = shouldThrow<ApiException> { catalog.products() }

            failure.status shouldBe HttpStatus.BAD_GATEWAY
            failure.key shouldBe ApiException.KEY_UNEXPECTED
            failure.code shouldBe null
        }

        "the headers hook runs per request, not once at build time" {
            // Which is what makes it usable for the header that actually varies — a token read off
            // the current request. `defaultHeaders` would pin the first caller's value onto every
            // later call.
            val seen = CopyOnWriteArrayList<HttpHeaders>()
            var token = "first"
            val catalog =
                httpClient<Catalog>(
                    baseUrl = "https://catalog.test",
                    headers = { it.setBearerAuth(token) },
                ) { exchangeFunction(answering(HttpStatus.OK, "ok", seen)) }

            catalog.products()
            token = "second"
            catalog.products()

            seen.map { it.getFirst(HttpHeaders.AUTHORIZATION) } shouldBe listOf("Bearer first", "Bearer second")
        }

        "one factory serves several interfaces against the same upstream" {
            // The shape an e2e spec wants: the codecs, filters and converters a generated client
            // needs are configured once, and each tag's interface is taken off the finished factory.
            val factory =
                httpServiceFactory("https://catalog.test") {
                    exchangeFunction(answering(HttpStatus.OK, "ok"))
                }

            factory.withClient<Catalog>().products() shouldBe "ok"
            factory.withClient<Warehouse>().stock() shouldBe "ok"
        }

        "the proxy factory has an escape hatch of its own" {
            // Spring writes an enum argument with `Enum.name()` and never consults `toString()`,
            // so without a converter this asks for `/products/TOP_SHELF` — a request that succeeds
            // against nothing. A generated client registers `registerApiEnumConverters` through
            // exactly this parameter; here the converter is written out so the spec needs no
            // generated code.
            val asked = CopyOnWriteArrayList<String>()
            val conversions =
                DefaultFormattingConversionService().apply {
                    addConverter(Grade::class.java, String::class.java) { it.wireValue }
                }

            val catalog =
                httpClient<Catalog>(
                    "https://catalog.test",
                    configure = {
                        exchangeFunction { request ->
                            asked += request.url().path
                            Mono.just(
                                ClientResponse
                                    .create(HttpStatus.OK)
                                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .body("one product")
                                    .build(),
                            )
                        }
                    },
                    factory = { conversionService(conversions) },
                )

            catalog.byGrade(Grade.TOP_SHELF) shouldBe "one product"
            asked.single() shouldBe "/products/top-shelf"
        }
    })
