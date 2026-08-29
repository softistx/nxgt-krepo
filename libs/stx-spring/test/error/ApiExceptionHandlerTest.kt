package com.strange.spring.error

import com.strange.i18n.Messages
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import java.util.Locale

/**
 * What a caller actually receives — the status, the translated text, and whether the debug message
 * came with it.
 *
 * A mock exchange rather than a running server: the decisions being checked are all made in the
 * handler, and a `WebTestClient` would add a second thing that can fail for every one of them.
 */
class ApiExceptionHandlerTest :
    StringSpec({
        val messages =
            Messages.of(
                Locale.ENGLISH to mapOf("orders.not-found" to "No order {id}"),
                Locale.FRENCH to mapOf("orders.not-found" to "Aucune commande {id}"),
            )

        fun exchange(acceptLanguage: String? = null) =
            MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders/7").apply { acceptLanguage?.let { header("Accept-Language", it) } },
            )

        fun handler(includeDebugMessage: Boolean = false) =
            ApiExceptionHandler(messages, ErrorProperties(enabled = true, includeDebugMessage = includeDebugMessage))

        "the thrower's status is the response's status" {
            val response = handler().handle(ApiException.notFound("orders.not-found"), exchange())

            response.statusCode shouldBe HttpStatus.NOT_FOUND
            response.body!!.status shouldBe "NOT_FOUND"
        }

        "the message key is translated, with its arguments" {
            val failure = ApiException.notFound("orders.not-found", mapOf("id" to 7))

            handler().handle(failure, exchange()).body!!.message shouldBe "No order 7"
        }

        "the locale comes from the request, not from a thread" {
            val failure = ApiException.notFound("orders.not-found", mapOf("id" to 7))

            handler().handle(failure, exchange(acceptLanguage = "fr")).body!!.message shouldBe "Aucune commande 7"
        }

        "the key is the code when the thrower named none" {
            // A client branches on this, so it has to be the stable half — never the translated text.
            handler().handle(ApiException.notFound("orders.not-found"), exchange()).body!!.code shouldBe "orders.not-found"
        }

        "an explicit code wins over the key" {
            val failure = ApiException.notFound("orders.not-found", code = "ORDER_404")

            handler().handle(failure, exchange()).body!!.code shouldBe "ORDER_404"
        }

        "the debug message is withheld unless the deployment asked for it" {
            val failure = ApiException.notFound("orders.not-found", debugMessage = "select * from orders where id = 7")

            handler()
                .handle(failure, exchange())
                .body!!
                .debugMessage
                .shouldBeNull()
            handler(includeDebugMessage = true).handle(failure, exchange()).body!!.debugMessage shouldBe
                "select * from orders where id = 7"
        }

        "a key no catalog answers comes back as itself rather than as an empty body" {
            handler().handle(ApiException.internal("errors.unheard-of"), exchange()).body!!.message shouldBe
                "errors.unheard-of"
        }
    })
