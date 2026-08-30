package com.strange.graphix.execute

import com.strange.graphix.GraphixError
import com.strange.graphix.GraphixResult
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import java.util.concurrent.CompletionException

/**
 * graphql-java wraps a resolver throw in `ExceptionWhileDataFetching`. The message here is
 * the cause's, not that wrapper's `": null"`.
 */
internal fun ExecutionResult.toGraphixResult(): GraphixResult =
    GraphixResult(
        data = getData<Map<String, Any?>?>(),
        errors =
            errors.map { error ->
                GraphixError(
                    message = error.unwrappedMessage(),
                    path = error.path.orEmpty(),
                )
            },
    )

private fun graphql.GraphQLError.unwrappedMessage(): String {
    val exception = (this as? ExceptionWhileDataFetching)?.exception
    val cause = generateSequence(exception) { it.cause }.firstOrNull { it !is CompletionException && it.message != null }
    return cause?.message ?: message ?: toString()
}
