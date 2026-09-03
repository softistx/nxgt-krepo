package com.softistx.graphix.error

import com.softistx.graphix.GraphixError

/**
 * Marks a class holding [ExceptionMapping] functions, so a container can be asked for every one.
 *
 * ```kotlin
 * @Singleton
 * class CatalogErrors : GraphixExceptionHandler {
 *     @ExceptionMapping
 *     fun notFound(error: GraphixError, failure: ProductNotFound): GraphixError =
 *         error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND)
 *
 *     @ExceptionMapping
 *     suspend fun denied(failure: AccessDenied, call: ApplicationCall): GraphixError? =
 *         if (audit.isInternal(call)) null else error.withErrorType(FORBIDDEN)
 * }
 * ```
 *
 * **Why an interface as well as an annotation.** The annotation says which *functions* are handlers;
 * the interface is what lets a container enumerate the *classes*. Spring can be asked for either,
 * but Koin's `getAll<T>()` answers only "every single bound to T" — a question only a type can ask.
 * It is the same split `GraphixResolver` makes in `stx-graphix-koin`, for the same reason.
 *
 * Registered on the builder with `exceptionHandler(...)`, or collected: a Spring bean, a Koin single,
 * an `errors { }` block under Ktor.
 */
interface GraphixExceptionHandler

/**
 * One exception type, handled.
 *
 * The **exception parameter** is the first parameter that is a `Throwable` and is not something the
 * framework supplies — the same election the parent source gets on a `@SchemaMapping`, so parameter
 * order is free. Every other parameter is either [GraphixError] (the error as it stands, to edit) or
 * a framework parameter: a `DataFetchingEnvironment`, graphql-java's `GraphQLContext`, or a type
 * registered with `contextParameter(...)`. There are no `@Argument`s here — a handler is not a field.
 *
 * The function may be `suspend`. Returning `null` means *not mine*: dispatch carries on to the next
 * less specific handler, then to `fallback { }`, then to what would have happened anyway.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExceptionMapping
