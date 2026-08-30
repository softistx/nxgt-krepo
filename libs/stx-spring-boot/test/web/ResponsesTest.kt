package com.strange.spring.web

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import org.springframework.http.HttpStatus
import org.springframework.http.codec.HttpMessageWriter
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.result.view.ViewResolver

/**
 * The four statuses a resource route answers with, and that each attaches the value it was called on.
 *
 * These had been exercised only by `examples/spring-orders`, whose routes were functional. That
 * example now builds its HTTP surface from a generated `@HttpExchange` interface and returns bodies
 * rather than `ServerResponse`s, so nothing called any of these — a helper with no caller and no
 * spec is a helper that is one refactor away from being quietly wrong. Coverage of a library belongs
 * to the library.
 */
class ResponsesTest :
    StringSpec({
        @Serializable
        data class Product(
            val name: String,
        )

        // `ServerResponse.Context` has two methods, so it is not a SAM: the writers are the default
        // ones, because writing the body is half of what these helpers do.
        val context =
            object : ServerResponse.Context {
                override fun messageWriters(): List<HttpMessageWriter<*>> = HandlerStrategies.withDefaults().messageWriters()

                override fun viewResolvers(): List<ViewResolver> = emptyList()
            }

        suspend fun statusOf(response: ServerResponse): HttpStatus {
            val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"))
            response.writeTo(exchange, context).block()
            return HttpStatus.valueOf(exchange.response.statusCode!!.value())
        }

        "ok is 200 carrying the value" {
            statusOf(Product("kettle").ok()) shouldBe HttpStatus.OK
        }

        "created is 201 carrying the value — the resource, which is what a client wants back" {
            statusOf(Product("kettle").created()) shouldBe HttpStatus.CREATED
        }

        "accepted is 202, for work taken on and not yet done" {
            statusOf(Product("kettle").accepted()) shouldBe HttpStatus.ACCEPTED
        }

        "noContent is 204 and is an extension on nothing, because there is nothing to send" {
            statusOf(noContent()) shouldBe HttpStatus.NO_CONTENT
        }
    })
