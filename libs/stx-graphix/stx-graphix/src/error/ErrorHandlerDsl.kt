package com.softistx.graphix.error

import com.softistx.graphix.GraphixBuilder
import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixException
import kotlin.reflect.KClass

/**
 * The `errors { }` block: what a thrown exception becomes.
 *
 * ```kotlin
 * Graphix {
 *     resolvers(ProductQueries(store))
 *     errors {
 *         on<ProductNotFound> { failure -> error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND) }
 *         fallback { error.withMessage("Internal error").withErrorType(INTERNAL_ERROR) }
 *     }
 * }
 * ```
 *
 * Shaped like [ScalarSpec][com.softistx.graphix.scalar.ScalarSpec]: a spec of lambdas, each running
 * with a receiver that carries the operation. The blocks are `suspend`, so a handler that has to ask
 * a service what the error should say can do it without blocking the engine thread.
 *
 * `on<T> { }` is sugar over a [GraphixExceptionHandler] that declines anything but `T` — there is
 * one mechanism, not two, and [handler] registers the interface directly.
 */
class GraphixErrorSpec internal constructor() {
    internal val handlers = mutableListOf<GraphixExceptionHandler>()
    internal var fallbackHandler: GraphixExceptionHandler? = null

    /** Handles [type] and its subclasses. Anything else falls through to the next handler. */
    fun <T : Throwable> on(
        type: KClass<T>,
        block: suspend GraphixErrorScope.(T) -> GraphixError?,
    ) {
        handlers +=
            GraphixExceptionHandler { failure ->
                @Suppress("UNCHECKED_CAST")
                if (type.isInstance(failure)) block(failure as T) else null
            }
    }

    /** Registers [handler] as it stands. Written as a block, this is `on<Throwable> { }`. */
    fun handler(handler: GraphixExceptionHandler) {
        handlers += handler
    }

    /**
     * Everything no handler claimed, asked last whatever the registration order.
     *
     * It has to be last by construction rather than by position: handlers also arrive from a
     * container, where nothing decides the order they were declared in.
     *
     * Without one, an unclaimed exception keeps the answer it would have had — this library does not
     * quietly start hiding messages because an unrelated handler was registered somewhere.
     */
    fun fallback(block: suspend GraphixErrorScope.(Throwable) -> GraphixError?) {
        if (fallbackHandler != null) throw GraphixException("errors { } already has a fallback")
        fallbackHandler = GraphixExceptionHandler { failure -> block(failure) }
    }
}

/** [GraphixErrorSpec.on], with the exception type read off the block. */
inline fun <reified T : Throwable> GraphixErrorSpec.on(noinline block: suspend GraphixErrorScope.(T) -> GraphixError?) = on(T::class, block)

/**
 * Turns exceptions into GraphQL errors.
 *
 * Adds to whatever is already registered — an `errors { }` block, a handler collected from a
 * container, and a hand-written one all go into the same ordered list, and the first to answer wins.
 */
fun GraphixBuilder.errors(block: GraphixErrorSpec.() -> Unit) {
    val spec = GraphixErrorSpec().apply(block)
    spec.handlers.forEach { addErrorHandler(it) }
    spec.fallbackHandler?.let { addErrorFallback(it) }
}

/**
 * Registers one handler.
 *
 * This is what an integration calls for each [GraphixExceptionHandler] it found — a Spring bean, a
 * Koin single. Writing it by hand is the same call.
 */
fun GraphixBuilder.exceptionHandler(handler: GraphixExceptionHandler) {
    addErrorHandler(handler)
}
