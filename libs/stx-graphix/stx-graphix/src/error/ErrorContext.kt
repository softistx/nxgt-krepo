package com.softistx.graphix.error

import com.softistx.graphix.GraphixError
import com.softistx.graphix.message.GraphixMessages
import graphql.schema.DataFetchingEnvironment
import java.util.Locale
import kotlin.reflect.KClass

/**
 * What a handler may read.
 *
 * The seats differ in exactly one thing: on a data fetcher there is a [DataFetchingEnvironment] to
 * read, around the interceptor chain there is only the operation context map. Everything else about
 * dispatch is identical, so this is the one piece each seat builds for itself.
 */
internal class ErrorContext(
    val environment: DataFetchingEnvironment?,
    val messages: GraphixMessages,
    val locale: Locale,
    val lookup: (KClass<*>) -> Any?,
)

/**
 * The receiver of an `errors { on<T> { } }` block: the error so far, plus what the operation knows.
 *
 * ```kotlin
 * errors {
 *     on<ProductNotFound> { failure -> error.withMessage(message(NOT_FOUND_KEY)).withErrorType(NOT_FOUND) }
 * }
 * ```
 */
class GraphixErrorScope internal constructor(
    /** The error as it stands: message from the exception, `path` and `locations` already filled. */
    val error: GraphixError,
    private val context: ErrorContext,
) {
    /**
     * The failing field's environment, or `null` when the throw did not come from one — an
     * interceptor's does not.
     */
    val environment: DataFetchingEnvironment? get() = context.environment

    /** What is in the operation context under [key], the way an interceptor's `get` reads it. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: KClass<T>): T? = context.lookup(key) as T?

    /**
     * Text for [key] in the operation's language.
     *
     * A handler's message reaches a client, which is the same reason a coercion error is looked up by
     * key and locale rather than written in English at the throw site.
     */
    fun message(
        key: String,
        args: Map<String, Any> = emptyMap(),
    ): String = context.messages.message(context.locale, key, args)
}

/** [GraphixErrorScope.get], with the key read off the type. */
inline fun <reified T : Any> GraphixErrorScope.get(): T? = get(T::class)
