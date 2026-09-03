package com.softistx.graphix.error

import com.softistx.graphix.Graphix
import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixException
import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.GraphixResult
import com.softistx.graphix.message.GraphixMessages
import kotlinx.coroutines.CancellationException
import java.util.Locale
import kotlin.reflect.KClass

/**
 * The seat for a throw that never reached graphql-java.
 *
 * Two of them exist, and neither has a field to describe. An interceptor runs before the engine, so
 * its throw leaves `Graphix.execute` as whatever it was — a 500 rather than an `errors[]`. A
 * subscription's `Flow` throwing **mid-stream** leaves the returned flow at the collector, which
 * `GraphqlWsSession` catches and SSE does not. `ExceptionSeatTest` measures both.
 *
 * **Nothing changes until a handler claims it.** An unclaimed throw is rethrown, exactly as before,
 * for the same reason an unclaimed field throw keeps the message it had: registering a handler for
 * one exception type is not a decision about every other one. `fallback { }` is how an application
 * says it wants all of them.
 *
 * [GraphixException] is deliberately not offered here. It means the operation could not be
 * submitted — a subscription sent to `execute`, a document that never reached the engine — which is
 * the caller's bug and not a client's error, and turning it into an `errors[]` under a `fallback { }`
 * would hide it from the only person who can fix it.
 *
 * @return the result to answer with, or `null` when nothing claimed [failure].
 */
internal suspend fun Graphix.handleOutsideField(
    failure: Throwable,
    request: GraphixRequest,
    context: Map<KClass<*>, Any>,
): GraphixResult? {
    if (failure is CancellationException || failure is GraphixException) return null
    if (errorHandlers.isEmpty()) return null
    val unwrapped = failure.unwrapped()
    val errorContext =
        ErrorContext(
            environment = null,
            messages = (context[GraphixMessages::class] as? GraphixMessages) ?: messages,
            locale = request.locale ?: Locale.getDefault(),
            lookup = { key -> context[key] },
        )
    val starting = GraphixError(message = unwrapped.message ?: unwrapped::class.simpleName ?: "error")
    return errorHandlers
        .handle(failure, starting, errorContext)
        ?.let { GraphixResult(data = null, errors = listOf(it)) }
}
