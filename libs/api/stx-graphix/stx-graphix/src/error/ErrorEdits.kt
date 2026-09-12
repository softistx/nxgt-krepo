package com.softistx.graphix.error

import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixErrorLocation

/**
 * Partial edits to the error a handler was handed.
 *
 * Spring GraphQL passes a `GraphqlErrorBuilder<?>` into an `@GraphQlExceptionHandler` so the method
 * can override the message and leave the rest. [GraphixError] is already a `data class`, so `copy()`
 * *is* that builder and these are names for the copies that read well in a handler:
 *
 * ```kotlin
 * @ExceptionMapping
 * fun notFound(error: GraphixError, failure: ProductNotFound): GraphixError =
 *     error.withMessage("No product ${failure.id}").withErrorType(NOT_FOUND)
 * ```
 *
 * The error arrives with `path` and `locations` already filled from the failing field, which is the
 * half a handler should not have to reconstruct.
 */
fun GraphixError.withMessage(message: String): GraphixError = copy(message = message)

/** The classification, in the vocabulary [GraphixErrorType] defines. */
fun GraphixError.withErrorType(type: GraphixErrorType): GraphixError = copy(errorType = type.name)

/** The classification as a free string, for one [GraphixErrorType] does not name. */
fun GraphixError.withErrorType(type: String): GraphixError = copy(errorType = type)

/** Adds one entry to `extensions`, keeping what is already there. */
fun GraphixError.withExtension(
    name: String,
    value: Any?,
): GraphixError = copy(extensions = extensions + (name to value))

/** Merges [more] into `extensions`; a repeated key takes the new value. */
fun GraphixError.withExtensions(more: Map<String, Any?>): GraphixError = copy(extensions = extensions + more)

/** Replaces the path. Rarely needed — the error arrives carrying the failing field's. */
fun GraphixError.withPath(path: List<Any>): GraphixError = copy(path = path)

/** Replaces the document positions. Rarely needed, for the same reason as [withPath]. */
fun GraphixError.withLocations(locations: List<GraphixErrorLocation>): GraphixError = copy(locations = locations)
