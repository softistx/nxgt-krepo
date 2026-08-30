package com.strange.spring.error

import com.strange.i18n.Messages
import com.strange.spring.i18n.forRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange

/**
 * Turns an [ApiException] into an [ErrorResponse], translated for the caller.
 *
 * Registered by [ErrorAutoConfiguration] when `stx.errors.enabled` is true, and not otherwise: an
 * application that already has an advice of its own must not find a second one competing with it.
 *
 * **Nothing else is handled here.** There is no `handle(Exception)` catch-all, because it would turn
 * every unexpected failure into a response carrying a message written for a stack trace rather than
 * for a reader — a connection string, a constraint name, a row. Spring's own handling answers those
 * with a problem detail that gives nothing away, which is the right answer for a failure nobody
 * anticipated.
 */
@RestControllerAdvice
class ApiExceptionHandler(
    private val messages: Messages,
    private val properties: ErrorProperties,
) {
    @ExceptionHandler(ApiException::class)
    fun handle(
        failure: ApiException,
        exchange: ServerWebExchange,
    ): ResponseEntity<ErrorResponse> =
        errorResponse(
            status = failure.status,
            // The key doubles as the code, so a client branches on `orders.not-found` while a
            // human reads whatever the catalogs say that means today.
            code = failure.code ?: failure.key,
            message = messages.forRequest(exchange).translate(failure.key, failure.args),
            debugMessage = failure.debugMessage,
            properties = properties,
        )
}
