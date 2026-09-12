package com.softistx.graphix.error

import com.softistx.graphix.GraphixError

/**
 * Turns a thrown exception into the error a client should see, or declines it.
 *
 * ```kotlin
 * @Singleton
 * class CatalogErrors : GraphixExceptionHandler {
 *     override suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError? =
 *         when (failure) {
 *             is ProductNotFound -> error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND)
 *             is AccessDenied -> error.withErrorType(FORBIDDEN)
 *             else -> null
 *         }
 * }
 * ```
 *
 * `error` is the error as it stands — message from the unwrapped exception, `path` and `locations`
 * already filled from the failing field — so a handler that only classifies restates nothing. That
 * is the `GraphqlErrorBuilder<?>` slot of Spring GraphQL's `@GraphQlExceptionHandler`, in immutable
 * form: [GraphixError] is a `data class`, so `copy()` is the builder and
 * [withMessage]/[withErrorType]/[withExtension] are names for the copies.
 *
 * **`null` means *not mine*.** The next handler is asked, and if none claims it the exception keeps
 * exactly the answer it would have had.
 *
 * One interface and one function, because the type is the only thing a container can be asked for —
 * `Koin.getAll<T>()`, Spring's `ObjectProvider<T>`. Narrowing to a single exception type is the
 * `on<T> { }` block rather than a second mechanism.
 */
fun interface GraphixExceptionHandler {
    suspend fun GraphixErrorScope.handle(failure: Throwable): GraphixError?
}
