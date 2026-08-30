package com.strange.spring.client

import com.strange.spring.error.ApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.invoker.createClient
import reactor.core.publisher.Mono
import java.util.concurrent.CopyOnWriteArrayList

private interface Catalog {
    @GetExchange("/products")
    suspend fun products(): String
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
            val factory =
                httpServiceFactory("https://catalog.test") {
                    exchangeFunction(answering(HttpStatus.OK, "ok"))
                }

            factory.createClient<Catalog>().products() shouldBe "ok"
        }
    })
