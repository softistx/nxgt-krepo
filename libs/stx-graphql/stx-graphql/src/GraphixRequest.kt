package com.strange.graphql

/**
 * One GraphQL operation. [variables] are already-decoded JSON values — maps, lists, numbers,
 * strings, booleans — the way graphql-java wants them. HTTP layers parse the envelope and
 * hand this over.
 */
data class GraphixRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
    val operationName: String? = null,
)
