package com.softistx.graphix

import java.util.Locale

/**
 * One GraphQL operation. [variables] are already-decoded JSON values — maps, lists, numbers,
 * strings, booleans — the way graphql-java wants them. HTTP layers parse the envelope and
 * hand this over; they do not pass the raw JSON object through.
 *
 * @param query the GraphQL document
 * @param variables coerced values, not a `JsonObject`. Missing keys are omitted, not null
 * @param operationName which operation in [query] to run when the document names more than one
 * @param extensions the spec's request extension point, reachable from instrumentation
 * @param locale which language this operation is answered in — see [locale]
 */
data class GraphixRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap(),
    val operationName: String? = null,
    val extensions: Map<String, Any?> = emptyMap(),
    /**
     * The operation's locale, which every scalar's coercion errors are looked up in. The HTTP
     * integrations negotiate it from `Accept-Language`; `null` leaves graphql-java's default,
     * which is the **JVM's** default locale — the host's environment, not the client's request.
     */
    val locale: Locale? = null,
)
