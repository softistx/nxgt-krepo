package com.strange.graphql.execute

import com.strange.graphql.GraphQlError
import com.strange.graphql.GraphQlResult
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import java.util.concurrent.CompletionException

internal fun ExecutionResult.toGraphQlResult(): GraphQlResult =
    GraphQlResult(
        data = getData<Map<String, Any?>?>(),
        errors =
            errors.map { error ->
                GraphQlError(
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
