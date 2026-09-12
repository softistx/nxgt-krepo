package com.softistx.spring.web

import com.softistx.spring.error.ApiException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerRequest

/** A GET at [uri], as a functional route would receive it. No server, no socket. */
private fun request(uri: String): ServerRequest =
    ServerRequest.create(
        MockServerWebExchange.from(MockServerHttpRequest.get(uri)),
        HandlerStrategies.withDefaults().messageReaders(),
    )

class RequestsTest :
    StringSpec({
        "a page is one-based on the wire and zero-based in the code" {
            request("/products?page=1").page shouldBe 0
            request("/products?page=3").page shouldBe 2
        }

        "a page nobody sent is the first one" {
            request("/products").page shouldBe 0
        }

        "a page that is not a number, or is before the first, is the first" {
            // How a hand-written link arrives. Answering page one is more useful than a 400 on a
            // parameter the caller did not mean to send.
            request("/products?page=abc").page shouldBe 0
            request("/products?page=-4").page shouldBe 0
            request("/products?page=0").page shouldBe 0
        }

        "a size is taken as sent, and otherwise defaulted" {
            request("/products?size=50").size shouldBe 50
            request("/products").size shouldBe DEFAULT_PAGE_SIZE
        }

        "a size of zero or less is not a size" {
            request("/products?size=0").size shouldBe DEFAULT_PAGE_SIZE
            request("/products?size=-1").size shouldBe DEFAULT_PAGE_SIZE
            request("/products?size=lots").size shouldBe DEFAULT_PAGE_SIZE
        }

        "paged is off unless it is asked for" {
            request("/products").paged shouldBe false
            request("/products?paged=true").paged shouldBe true
        }

        "a required parameter that is missing names itself" {
            val failure = shouldThrow<ApiException> { request("/products").requiredParam("category") }

            failure.status shouldBe HttpStatus.BAD_REQUEST
            failure.key shouldBe "params.required"
            // The whole point of the argument: a catalog can say which parameter, and a client with
            // six of them does not have to guess.
            failure.args shouldBe mapOf("name" to "category")
        }

        "a required parameter that is present is just the value" {
            request("/products?category=tools").requiredParam("category") shouldBe "tools"
        }

        "a list parameter splits on commas, and stays null when absent" {
            request("/products?tags=new,sale").listParam("tags") shouldBe listOf("new", "sale")
            request("/products").listParam("tags") shouldBe null
            request("/products?tags=new").listParam("tags") shouldBe listOf("new")
        }

        "a required list parameter fails the same way a required one does" {
            shouldThrow<ApiException> { request("/products").requiredListParam("tags") }
            request("/products?tags=new,sale").requiredListParam("tags") shouldBe listOf("new", "sale")
        }

        "the sort parameter is read off the request" {
            request("/products?sort=name:ASC").sort shouldBe listOf(SortOrder("name"))
            request("/products").sort shouldBe emptyList()
        }
    })
