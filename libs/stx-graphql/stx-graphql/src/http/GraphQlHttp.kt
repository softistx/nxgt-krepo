package com.strange.graphql.http

import com.strange.graphql.GraphQlError
import com.strange.graphql.GraphQlRequest
import com.strange.graphql.GraphQlResult
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
 */
@Serializable
data class GraphQlHttpRequest(
    val query: String? = null,
    val operationName: String? = null,
    val variables: JsonObject? = null,
)

@Serializable
data class GraphQlHttpResponse(
    val data: JsonElement? = null,
    val errors: List<GraphQlHttpError>? = null,
)

@Serializable
data class GraphQlHttpError(
    val message: String,
    val path: List<JsonElement> = emptyList(),
)

class BadGraphQlHttp(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

fun GraphQlHttpRequest.toGraphQlRequest(): GraphQlRequest {
    val query = query ?: throw BadGraphQlHttp("a GraphQL request needs a query")
    return GraphQlRequest(
        query = query,
        operationName = operationName,
        variables = variables?.mapValues { it.value.toJava() } ?: emptyMap(),
    )
}

fun GraphQlResult.toHttp(): GraphQlHttpResponse =
    GraphQlHttpResponse(
        data = data?.toJsonElement(),
        errors = errors.takeIf { it.isNotEmpty() }?.map { it.toHttp() },
    )

private fun GraphQlError.toHttp(): GraphQlHttpError =
    GraphQlHttpError(
        message = message,
        path = path.map { it.toJsonPrimitive() },
    )

private fun Any.toJsonPrimitive(): JsonElement =
    when (this) {
        is Number -> JsonPrimitive(toLong())
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

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
