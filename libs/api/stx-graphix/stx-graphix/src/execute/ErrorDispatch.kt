package com.softistx.graphix.execute

import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixErrorLocation
import com.softistx.graphix.error.ErrorContext
import com.softistx.graphix.error.ErrorHandlers
import com.softistx.graphix.error.unwrapped
import com.softistx.graphix.message.GraphixMessages
import graphql.ErrorClassification
import graphql.ErrorType
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.SimpleDataFetcherExceptionHandler
import graphql.language.SourceLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.Locale
import java.util.concurrent.CompletableFuture

/**
 * The seat where a field's throw becomes an error the application chose.
 *
 * Falls through to graphql-java's own handler whenever nothing claimed the exception, so registering
 * a handler for one type does not change what every other type answers.
 */
internal fun errorDispatch(
    handlers: ErrorHandlers,
    messages: GraphixMessages,
): DataFetcherExceptionHandler =
    DataFetcherExceptionHandler { parameters ->
        val environment = parameters.dataFetchingEnvironment
        val scope = environment.graphQlContext.get<CoroutineScope>(OperationScope)
        if (scope == null) {
            // No scope means nothing this library built is running the operation. Answering with
            // graphql-java's default is strictly better than failing the field twice.
            SimpleDataFetcherExceptionHandler().handleException(parameters)
        } else {
            scope.future {
                val context =
                    ErrorContext(
                        environment = environment,
                        messages = environment.graphQlContext.get<GraphixMessages>(GraphixMessages::class) ?: messages,
                        locale = environment.locale ?: Locale.getDefault(),
                        lookup = { key -> environment.graphQlContext.get<Any>(key) },
                    )
                val handled = handlers.handle(parameters.exception, parameters.startingError(), context)
                handled
                    ?.let { DataFetcherExceptionHandlerResult.newResult().error(it.toGraphQLError()).build() }
                    ?: SimpleDataFetcherExceptionHandler().handleException(parameters).await()
            }
        }
    }

/**
 * The error a handler starts from: what the exception said, where it happened.
 *
 * `path` and `locations` are the half a handler should never have to reconstruct, and the message is
 * the unwrapped exception's — the same text that would have reached the client with no handler
 * registered, so `error` unedited is exactly today's answer.
 */
private fun DataFetcherExceptionHandlerParameters.startingError(): GraphixError {
    val failure = exception.unwrapped()
    return GraphixError(
        message = failure.message ?: failure::class.simpleName ?: "error",
        path = path?.toList().orEmpty(),
        locations = listOfNotNull(sourceLocation?.let { GraphixErrorLocation(it.line, it.column) }),
        errorType = ErrorType.DataFetchingException.name,
    )
}

/**
 * A [GraphixError] as graphql-java's interface.
 *
 * Written out rather than built with `GraphqlErrorBuilder`: that builder is F-bounded
 * (`B extends GraphqlErrorBuilder<B>`), and inferring the type argument from Kotlin puts the compiler
 * into a `StackOverflowError` with no file pointer. `getMessage`, `getLocations` and `getErrorType`
 * are the three the interface leaves abstract.
 */
private fun GraphixError.toGraphQLError(): GraphQLError {
    val error = this
    return object : GraphQLError {
        override fun getMessage(): String = error.message

        override fun getLocations(): List<SourceLocation> = error.locations.map { SourceLocation(it.line, it.column) }

        // graphql-java's own factory: its `toString()` is the string, which is what both
        // `toSpecification` and `Results.toGraphixResult` read the classification back out of.
        override fun getErrorType(): ErrorClassification = ErrorClassification.errorClassification(error.errorType ?: "")

        override fun getPath(): List<Any>? = error.path.takeIf { it.isNotEmpty() }

        // graphql-java declares `Map<String, Object>`; a GraphQL extension value may legitimately be
        // null, so the cast is the honest spelling rather than dropping the null entries.
        @Suppress("UNCHECKED_CAST")
        override fun getExtensions(): Map<String, Any>? = error.extensions.takeIf { it.isNotEmpty() } as Map<String, Any>?
    }
}
