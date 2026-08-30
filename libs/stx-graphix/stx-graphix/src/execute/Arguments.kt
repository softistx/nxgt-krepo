package com.strange.graphix.execute

import com.strange.graphix.GraphixException
import com.strange.graphix.schema.GraphQLContext
import com.strange.graphix.schema.graphQLName
import graphql.schema.DataFetchingEnvironment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters

/**
 * Binds GraphQL arguments and `@GraphQLContext` parameters onto [function].
 *
 * An optional Kotlin parameter with no argument is omitted so `callBy` uses the default.
 * `@GraphQLContext` is looked up by `KClass` and is not a GraphQL argument.
 */
internal fun bindArguments(
    function: KFunction<*>,
    environment: DataFetchingEnvironment,
    json: Json,
): Map<KParameter, Any?> {
    val bound = LinkedHashMap<KParameter, Any?>()
    function.valueParameters.forEach { parameter ->
        if (parameter.findAnnotation<GraphQLContext>() != null) {
            bound[parameter] = contextValue(parameter, environment)
            return@forEach
        }
        val raw: Any? = environment.getArgument(parameter.graphQLName())
        if (raw == null && parameter.isOptional) return@forEach
        bound[parameter] = decode(raw, parameter, json)
    }
    return bound
}

private fun contextValue(
    parameter: KParameter,
    environment: DataFetchingEnvironment,
): Any {
    val classifier =
        parameter.type.classifier as? kotlin.reflect.KClass<*>
            ?: throw GraphixException("@GraphQLContext ${parameter.name} needs a class type")
    return environment.graphQlContext.get<Any>(classifier)
        ?: throw GraphixException("no ${classifier.qualifiedName} in the operation context")
}

private fun decode(
    raw: Any?,
    parameter: KParameter,
    json: Json,
): Any? {
    if (raw == null) return null
    val serializer = json.serializersModule.serializer(parameter.type)
    val element = raw.toJsonElement()
    return json.decodeFromJsonElement(serializer, element)
}

private fun Any.toJsonElement(): JsonElement =
    when (this) {
        is JsonElement -> {
            this
        }

        is Map<*, *> -> {
            JsonObject(
                entries.associate { (key, value) ->
                    key.toString() to (value?.toJsonElement() ?: JsonNull)
                },
            )
        }

        is List<*> -> {
            JsonArray(map { it?.toJsonElement() ?: JsonNull })
        }

        is Number -> {
            JsonPrimitive(this)
        }

        is Boolean -> {
            JsonPrimitive(this)
        }

        is String -> {
            JsonPrimitive(this)
        }

        else -> {
            JsonPrimitive(toString())
        }
    }
