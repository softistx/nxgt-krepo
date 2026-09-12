package com.softistx.graphix.error

import com.softistx.graphix.GraphixError
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException

/**
 * Every handler an engine was built with, in the order they were registered.
 *
 * **Order decides**, the way it already does for interceptors: each is asked in turn and the first
 * to answer wins, so a handler for one exception type goes in before a broader one that would also
 * claim it. [fallback] is the exception to that rule and is always asked last — it has to be, since
 * handlers arriving from a container have no registration order anyone controls.
 *
 * Nothing answering at all leaves the error exactly as it would have been.
 */
internal class ErrorHandlers(
    private val handlers: List<GraphixExceptionHandler>,
    private val fallback: GraphixExceptionHandler?,
) {
    fun isEmpty(): Boolean = handlers.isEmpty() && fallback == null

    /**
     * Runs the first handler that claims [failure], starting from [error].
     *
     * @return the edited error, or `null` when nothing claimed it.
     */
    suspend fun handle(
        failure: Throwable,
        error: GraphixError,
        context: ErrorContext,
    ): GraphixError? {
        val unwrapped = failure.unwrapped()
        val scope = GraphixErrorScope(error, context)
        handlers.forEach { handler ->
            with(handler) { scope.handle(unwrapped) }?.let { return it }
        }
        return fallback?.let { with(it) { scope.handle(unwrapped) } }
    }
}

/**
 * The exception the application actually threw.
 *
 * Two wrappers stand between a resolver and here, and which one depends on how the resolver was
 * written: a plain function is invoked with `callBy`, so reflection wraps its throw in
 * `InvocationTargetException`; a `suspend` one travels as a failed future, so it arrives inside a
 * `CompletionException`. `ExceptionSeatTest` measures both. Unwrapping one and not the other would
 * dispatch half the resolvers in an application and silently miss the rest.
 *
 * The identity check is not paranoia: a `Throwable` whose `cause` is itself is legal, and the loop
 * would not end.
 */
internal fun Throwable.unwrapped(): Throwable {
    var current: Throwable = this
    while (current is InvocationTargetException || current is CompletionException) {
        val cause = current.cause ?: return current
        if (cause === current) return current
        current = cause
    }
    return current
}
