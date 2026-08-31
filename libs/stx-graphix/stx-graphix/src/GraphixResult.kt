package com.strange.graphix

/**
 * What graphql-java returned, without leaking `ExecutionResult`. A field error is in [errors]
 * with HTTP still 200 — that is the GraphQL contract, not an exception. [GraphixException] is
 * the other kind: the schema would not build, or the document never reached the engine.
 *
 * @param data the selection set, or `null` when the operation failed before producing any
 * @param errors GraphQL errors, including resolver throws unwrapped from graphql-java's wrapper
 */
data class GraphixResult(
    val data: Map<String, Any?>?,
    val errors: List<GraphixError> = emptyList(),
) {
    /** `true` when [errors] is empty. Partial data with errors is not ok. */
    val isOk: Boolean get() = errors.isEmpty()
}

/**
 * One GraphQL error. [path] mixes field names (`String`) and list indices (`Int`), in document
 * order.
 */
data class GraphixError(
    val message: String,
    /** Field names (`String`) and list indices (`Int`), in document order. */
    val path: List<Any> = emptyList(),
)
