package com.strange.spring.web

import com.strange.spring.error.ApiException
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.bodyToMono
import org.springframework.web.reactive.function.server.queryParamOrNull

// What a functional route reads off a request, without repeating the same four lines per handler.
// These are extensions rather than a base handler class for the reason stx-mongo and stx-jpa give:
// `body` reifies its type at the call site, which a method on a superclass cannot do.

/** The `id` path variable, which is what most routes here are keyed on. */
val ServerRequest.id: String get() = pathVariable("id")

/**
 * A query parameter that the route cannot proceed without.
 *
 * Fails as `params.required` with the parameter's name in the arguments, so a catalog can say
 * *"{name} is required"* and the client is told which one — a bare "a parameter is missing" leaves
 * the caller to guess, and guessing against a route with six parameters is a support ticket.
 */
fun ServerRequest.requiredParam(name: String): String =
    queryParamOrNull(name)
        ?: throw ApiException.badRequest("params.required", mapOf("name" to name))

/** A comma-separated parameter, or null when it is absent. `?tags=new,sale` reads as two tags. */
fun ServerRequest.listParam(name: String): List<String>? = queryParamOrNull(name)?.split(",")

/** [listParam], for a route that cannot proceed without it. */
fun ServerRequest.requiredListParam(name: String): List<String> = requiredParam(name).split(",")

/**
 * The requested page, zero-based, from a `?page=` that a client writes one-based.
 *
 * Nonsense is a zero rather than a failure: `?page=abc` and `?page=-4` are how a hand-written link
 * arrives, and answering the first page is more useful than a 400 on a parameter the client did not
 * mean to send. A parameter the route genuinely needs is [requiredParam], which does fail.
 */
val ServerRequest.page: Int get() = ((queryParamOrNull("page")?.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)

/** The requested page size, defaulting to [DEFAULT_PAGE_SIZE] and never zero or negative. */
val ServerRequest.size: Int get() = queryParamOrNull("size")?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_PAGE_SIZE

/** Whether the caller asked for a page at all: `?paged=true`. */
val ServerRequest.paged: Boolean get() = queryParamOrNull("paged").toBoolean()

/** How many results a caller gets when it does not say. */
const val DEFAULT_PAGE_SIZE = 20

/**
 * The body, decoded as [T].
 *
 * `awaitSingle` and not `awaitSingleOrNull`: a route asking for a body of a non-null type wants a
 * failure when there is none, and Spring's own message about a missing body says more than a
 * `NullPointerException` three frames later.
 */
suspend inline fun <reified T : Any> ServerRequest.body(): T = bodyToMono<T>().awaitSingle()
