package com.softistx.graphix.error

import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixException
import com.softistx.graphix.message.GraphixMessages
import graphql.schema.DataFetchingEnvironment
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSubclassOf
import graphql.GraphQLContext as OperationContext

/**
 * What a handler may read, and where its framework parameters come from.
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
) {
    /**
     * One framework parameter of a handler function.
     *
     * The same three branches as a resolver's — the environment, graphql-java's context bag, then the
     * operation context by `KClass` — with the first two answering "not here" rather than null when
     * the seat has no environment, since a handler asking for a `DataFetchingEnvironment` around the
     * interceptor chain is a mistake worth naming.
     */
    fun frameworkValue(parameter: KParameter): Any {
        val classifier =
            parameter.type.classifier as? KClass<*>
                ?: throw GraphixException("${parameter.name} needs a class type")
        if (classifier.isSubclassOf(DataFetchingEnvironment::class)) {
            return environment ?: throw GraphixException(noEnvironment(parameter, "DataFetchingEnvironment"))
        }
        if (classifier.isSubclassOf(OperationContext::class)) {
            return environment?.graphQlContext ?: throw GraphixException(noEnvironment(parameter, "GraphQLContext"))
        }
        return lookup(classifier)
            ?: throw GraphixException("no ${classifier.qualifiedName} in the operation context")
    }

    private fun noEnvironment(
        parameter: KParameter,
        type: String,
    ): String =
        "'${parameter.name}' is a $type, and this failure happened outside a field — an interceptor " +
            "throw has no field to describe. Read the operation context instead."
}

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
