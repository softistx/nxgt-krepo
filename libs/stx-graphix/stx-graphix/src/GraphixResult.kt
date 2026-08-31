package com.strange.graphix

/**
 * What graphql-java returned, without leaking `ExecutionResult`. A field error is in [errors]
 * with HTTP still 200 — that is the GraphQL contract, not an exception. [GraphixException] is
 * the other kind: the schema would not build, or the document never reached the engine.
 *
 * @param data the selection set, or `null` when the operation failed before producing any
 * @param errors GraphQL errors, including resolver throws unwrapped from graphql-java's wrapper
 * @param extensions whatever instrumentation put in the result's `extensions`, usually empty
 */
data class GraphixResult(
    val data: Map<String, Any?>?,
    val errors: List<GraphixError> = emptyList(),
    val extensions: Map<String, Any?> = emptyMap(),
) {
    /** `true` when [errors] is empty. Partial data with errors is not ok. */
    val isOk: Boolean get() = errors.isEmpty()
}

/**
 * One GraphQL error. [path] mixes field names (`String`) and list indices (`Int`), in document
 * order.
 *
 * [locations], [extensions] and [errorType] are the rest of what the spec allows an error to
 * carry, and what a client library reads to tell a validation failure from a resolver throw.
 */
data class GraphixError(
    val message: String,
    /** Field names (`String`) and list indices (`Int`), in document order. */
    val path: List<Any> = emptyList(),
    /** Where in the document the error is, when the engine knows. */
    val locations: List<GraphixErrorLocation> = emptyList(),
    /** Error metadata, the spec's own extension point. */
    val extensions: Map<String, Any?> = emptyMap(),
    /** graphql-java's classification: `ValidationError`, `DataFetchingException`, and so on. */
    val errorType: String? = null,
)

/** A position in the GraphQL document, 1-based. */
data class GraphixErrorLocation(
    val line: Int,
    val column: Int,
)
