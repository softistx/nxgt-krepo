package com.strange.graphix

/**
 * What graphql-java returned, without leaking `ExecutionResult`. A field error is in [errors]
 * with HTTP still 200 — that is the GraphQL contract, not an exception.
 */
data class GraphixResult(
    val data: Map<String, Any?>?,
    val errors: List<GraphixError> = emptyList(),
) {
    val isOk: Boolean get() = errors.isEmpty()
}

data class GraphixError(
    val message: String,
    val path: List<Any> = emptyList(),
)
