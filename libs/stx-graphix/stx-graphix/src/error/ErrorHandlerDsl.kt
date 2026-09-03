package com.softistx.graphix.error

import com.softistx.graphix.GraphixBuilder
import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixException
import kotlin.reflect.KClass

/**
 * The `errors { }` block: exception types mapped to the GraphQL error each should become.
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
 */
class GraphixErrorSpec internal constructor() {
    internal val handlers = LinkedHashMap<KClass<out Throwable>, ErrorHandling>()
    internal var fallbackHandler: ErrorHandling? = null

    /** Handles [type] and its subclasses, unless a more specific handler claims one of them. */
    fun <T : Throwable> on(
        type: KClass<T>,
        block: suspend GraphixErrorScope.(T) -> GraphixError?,
    ) {
        if (handlers.containsKey(type)) {
            throw GraphixException("errors { } already handles ${type.qualifiedName}")
        }
        handlers[type] =
            ErrorHandling { failure, error, context ->
                @Suppress("UNCHECKED_CAST")
                GraphixErrorScope(error, context).block(failure as T)
            }
    }

    /**
     * Everything no handler claimed.
     *
     * Without one, an unclaimed exception keeps the answer it would have had — this library does not
     * quietly start hiding messages because an unrelated handler was registered somewhere.
     */
    fun fallback(block: suspend GraphixErrorScope.(Throwable) -> GraphixError?) {
        if (fallbackHandler != null) throw GraphixException("errors { } already has a fallback")
        fallbackHandler =
            ErrorHandling { failure, error, context -> GraphixErrorScope(error, context).block(failure) }
    }
}

/** [GraphixErrorSpec.on], with the exception type read off the block. */
inline fun <reified T : Throwable> GraphixErrorSpec.on(noinline block: suspend GraphixErrorScope.(T) -> GraphixError?) = on(T::class, block)

/**
 * Turns exceptions into GraphQL errors.
 *
 * Adds to whatever is already registered — an `errors { }` block, a `GraphixExceptionHandler` class,
 * and a bean collected from a container all land in the same index, and two of them claiming the same
 * exception type is refused rather than one silently winning.
 */
fun GraphixBuilder.errors(block: GraphixErrorSpec.() -> Unit) {
    val spec = GraphixErrorSpec().apply(block)
    spec.handlers.forEach { (type, handler) -> addErrorHandler(type, handler) }
    spec.fallbackHandler?.let { addErrorFallback(it) }
}

/**
 * Registers a class of `@ExceptionMapping` functions.
 *
 * This is what an integration calls for each `GraphixExceptionHandler` it found — a Spring bean, a
 * Koin single. Writing it by hand is the same call.
 */
fun GraphixBuilder.exceptionHandler(handler: GraphixExceptionHandler) {
    addExceptionHandler(handler)
}
