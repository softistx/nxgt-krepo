package com.strange.graphql

/**
 * What graphql-java returned, without leaking `ExecutionResult`. A field error is in [errors]
 * with HTTP still 200 — that is the GraphQL contract, not an exception.
 */
data class GraphQlResult(
    val data: Map<String, Any?>?,
    val errors: List<GraphQlError> = emptyList(),
) {
    val isOk: Boolean get() = errors.isEmpty()
}

data class GraphQlError(
    val message: String,
    val path: List<Any> = emptyList(),
)
