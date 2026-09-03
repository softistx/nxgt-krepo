package com.softistx.graphix.http

import com.softistx.graphix.GraphixError
import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.GraphixResult
import com.softistx.graphix.json.toJava
import com.softistx.graphix.json.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * The JSON envelope both HTTP integrations speak. Ktor and Spring parse this; they do not
 * invent a second shape.
 *
 * [query] is nullable here because a malformed body may omit it — [toGraphixRequest] then
 * throws [BadGraphixHttp], which the HTTP layer turns into 400.
 */
@Serializable
data class GraphixHttpRequest(
    /** GraphQL document. Missing here is HTTP 400, not a GraphQL field error. */
    val query: String? = null,
    val operationName: String? = null,
    /** Still JSON. [toGraphixRequest] turns values into the `Map` graphql-java expects. */
    val variables: JsonObject? = null,
    /** The spec's request extension point — passed through to the operation untouched. */
    val extensions: JsonObject? = null,
)

/** `{ "data", "errors" }`. [errors] is omitted when empty, not an empty array. */
@Serializable
data class GraphixHttpResponse(
    val data: JsonElement? = null,
    /** Present only when there is at least one error — never an empty array. */
    val errors: List<GraphixHttpError>? = null,
    /** Present only when the operation produced any. */
    val extensions: JsonObject? = null,
)

/**
 * One GraphQL error in the HTTP envelope. [path] is JSON primitives, not
 * [com.softistx.graphix.GraphixError.path] — strings for fields, numbers for indices.
 */
@Serializable
data class GraphixHttpError(
    val message: String,
    val path: List<JsonElement> = emptyList(),
    /** Omitted rather than sent empty, the way the spec's examples read. */
    val locations: List<GraphixHttpErrorLocation>? = null,
    val extensions: JsonObject? = null,
)

/** A position in the GraphQL document, 1-based. */
@Serializable
data class GraphixHttpErrorLocation(
    val line: Int,
    val column: Int,
)

/**
 * The HTTP body could not become a [GraphixRequest]: missing `query`, or JSON that does not
 * match the envelope. Callers map this to HTTP 400. It is not a GraphQL field error.
 */
class BadGraphixHttp(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Requires [GraphixHttpRequest.query]. Variables become the `Map` graphql-java expects.
 *
 * [locale] is the operation's, which is what coercion errors are translated in — [acceptedLocale]
 * off the request's `Accept-Language` is where both HTTP integrations get it.
 */
fun GraphixHttpRequest.toGraphixRequest(locale: Locale? = null): GraphixRequest {
    val query = query ?: throw BadGraphixHttp("a GraphQL request needs a query")
    return GraphixRequest(
        query = query,
        operationName = operationName,
        variables = variables?.mapValues { it.value.toJava() } ?: emptyMap(),
        extensions = extensions?.mapValues { it.value.toJava() } ?: emptyMap(),
        locale = locale,
    )
}

/** [GraphixResult] as the HTTP envelope. Empty [GraphixResult.errors] become a missing `errors`. */
fun GraphixResult.toHttp(): GraphixHttpResponse =
    GraphixHttpResponse(
        data = data?.toJsonElement(),
        errors = errors.takeIf { it.isNotEmpty() }?.map { it.toHttp() },
        extensions = extensions.takeIf { it.isNotEmpty() }?.toJsonObject(),
    )

/** One SSE `data:` frame. HTTP plugins stream these for subscription operations. */
fun GraphixHttpResponse.toSse(json: Json): String = "data: ${json.encodeToString(GraphixHttpResponse.serializer(), this)}\n\n"

private fun GraphixError.toHttp(): GraphixHttpError =
    GraphixHttpError(
        message = message,
        path = path.map { it.toJsonPrimitive() },
        locations = locations.takeIf { it.isNotEmpty() }?.map { GraphixHttpErrorLocation(it.line, it.column) },
        extensions = extensions.takeIf { it.isNotEmpty() }?.toJsonObject(),
    )

private fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(mapValues { it.value.toJsonElement() })

private fun Any.toJsonPrimitive(): JsonElement =
    when (this) {
        is Number -> JsonPrimitive(toLong())
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }
