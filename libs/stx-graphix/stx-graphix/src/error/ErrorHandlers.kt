package com.softistx.graphix.error

import com.softistx.common.concurrent.Memo
import com.softistx.graphix.GraphixError
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import kotlin.reflect.KClass

/** One registered handler, however it was written — a reflected function or a DSL block. */
internal fun interface ErrorHandling {
    /** The edited error, or `null` for *not mine*. */
    suspend fun handle(
        failure: Throwable,
        error: GraphixError,
        context: ErrorContext,
    ): GraphixError?
}

/**
 * Every handler an engine was built with, indexed by the exception type each answers for.
 *
 * **Most specific wins.** Lookup walks the thrown exception's class and then its superclasses, so a
 * handler for `ProductNotFound` is asked before one for `RuntimeException`. The walk is over the Java
 * hierarchy rather than `allSuperclasses` because that one includes interfaces and does not order by
 * distance, and distance is the whole rule here.
 *
 * A handler answering `null` means *not mine*: the walk carries on to the next, less specific one,
 * and then to [fallback]. Nothing answering at all leaves the error exactly as it would have been.
 */
internal class ErrorHandlers(
    private val byType: Map<KClass<out Throwable>, ErrorHandling>,
    private val fallback: ErrorHandling?,
) {
    /** The candidates for a thrown class, outermost-specific first. Computed once per class. */
    private val candidates = Memo<KClass<*>, List<ErrorHandling>> { thrown -> byType.entriesFor(thrown) }

    fun isEmpty(): Boolean = byType.isEmpty() && fallback == null

    /**
     * Runs the first handler that claims [failure], starting from [error].
     *
     * @return the edited error, or `null` when no handler claimed it.
     */
    suspend fun handle(
        failure: Throwable,
        error: GraphixError,
        context: ErrorContext,
    ): GraphixError? {
        val unwrapped = failure.unwrapped()
        candidates[unwrapped::class].forEach { handler ->
            handler.handle(unwrapped, error, context)?.let { return it }
        }
        return fallback?.handle(unwrapped, error, context)
    }
}

private fun Map<KClass<out Throwable>, ErrorHandling>.entriesFor(thrown: KClass<*>): List<ErrorHandling> =
    generateSequence(thrown.java) { it.superclass }
        .mapNotNull { this[it.kotlin] }
        .toList()

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
