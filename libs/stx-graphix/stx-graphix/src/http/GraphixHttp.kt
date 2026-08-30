package com.strange.graphix.http

import com.strange.graphix.GraphixError
import com.strange.graphix.GraphixRequest
import com.strange.graphix.GraphixResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

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
)

/** `{ "data", "errors" }`. [errors] is omitted when empty, not an empty array. */
@Serializable
data class GraphixHttpResponse(
    val data: JsonElement? = null,
    /** Present only when there is at least one error — never an empty array. */
    val errors: List<GraphixHttpError>? = null,
)

/**
 * One GraphQL error in the HTTP envelope. [path] is JSON primitives, not
 * [com.strange.graphix.GraphixError.path] — strings for fields, numbers for indices.
 */
@Serializable
data class GraphixHttpError(
    val message: String,
    val path: List<JsonElement> = emptyList(),
)

/**
 * The HTTP body could not become a [GraphixRequest]: missing `query`, or JSON that does not
 * match the envelope. Callers map this to HTTP 400. It is not a GraphQL field error.
 */
class BadGraphixHttp(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Requires [GraphixHttpRequest.query]. Variables become the `Map` graphql-java expects. */
fun GraphixHttpRequest.toGraphixRequest(): GraphixRequest {
    val query = query ?: throw BadGraphixHttp("a GraphQL request needs a query")
    return GraphixRequest(
        query = query,
        operationName = operationName,
        variables = variables?.mapValues { it.value.toJava() } ?: emptyMap(),
    )
}

/** [GraphixResult] as the HTTP envelope. Empty [GraphixResult.errors] become a missing `errors`. */
fun GraphixResult.toHttp(): GraphixHttpResponse =
    GraphixHttpResponse(
        data = data?.toJsonElement(),
        errors = errors.takeIf { it.isNotEmpty() }?.map { it.toHttp() },
    )

private fun GraphixError.toHttp(): GraphixHttpError =
    GraphixHttpError(
        message = message,
        path = path.map { it.toJsonPrimitive() },
    )

private fun Any.toJsonPrimitive(): JsonElement =
    when (this) {
        is Number -> JsonPrimitive(toLong())
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

/** JsonElement as the Java values graphql-java wants: Map, List, Number, Boolean, String, null. */
internal fun JsonElement.toJava(): Any? =
    when (this) {
        is JsonNull -> {
            null
        }

        is JsonPrimitive -> {
            if (isString) {
                content
            } else {
                booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
            }
        }

        is JsonObject -> {
            mapValues { it.value.toJava() }
        }

        is JsonArray -> {
            map { it.toJava() }
        }
    }

internal fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
        is List<*> -> JsonArray(map { it.toJsonElement() })
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }
