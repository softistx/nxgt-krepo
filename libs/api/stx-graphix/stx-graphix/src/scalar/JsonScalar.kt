package com.softistx.graphix.scalar

import com.softistx.graphix.json.toJava
import com.softistx.graphix.json.toJsonElement
import com.softistx.graphix.message.MessageKeys
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.EnumValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * Arbitrary JSON, as a `kotlinx.serialization.json.JsonElement`. For the field that genuinely
 * holds a document — a webhook payload, a settings blob, a third party's response — and for
 * nothing else. A `Json` field tells a client's code generator nothing, so every field that has a
 * shape should be a type that says so.
 *
 * The Kotlin side is `JsonElement` rather than `Map<String, Any?>` because that is this stack's
 * JSON everywhere else; the wire side is whatever graphql-java hands the response writer.
 */
private object JsonCoercing : Coercing<JsonElement, Any> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Any =
        when (dataFetcherResult) {
            is JsonElement -> dataFetcherResult.toJava() ?: JsonNull
            else -> dataFetcherResult
        }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): JsonElement = input.toJsonElement()

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): JsonElement = element(input, variables, Coercion("Json", graphQLContext, locale))

    /** A literal is a tree; a variable inside one is already coerced, so it is read from [variables]. */
    private fun element(
        value: Value<*>,
        variables: CoercedVariables,
        coercion: Coercion,
    ): JsonElement =
        when (value) {
            is NullValue -> {
                JsonNull
            }

            is StringValue -> {
                JsonPrimitive(value.value)
            }

            is EnumValue -> {
                JsonPrimitive(value.name)
            }

            is BooleanValue -> {
                JsonPrimitive(value.isValue)
            }

            is IntValue -> {
                JsonPrimitive(value.value)
            }

            is FloatValue -> {
                JsonPrimitive(value.value)
            }

            is ArrayValue -> {
                JsonArray(value.values.map { element(it, variables, coercion) })
            }

            is ObjectValue -> {
                JsonObject(value.objectFields.associate { it.name to element(it.value, variables, coercion) })
            }

            is VariableReference -> {
                variables.toMap()[value.name].toJsonElement()
            }

            else -> {
                coercion.cannotAccept(value, MessageKeys.LITERAL_OBJECT)
            }
        }
}

/** Any JSON value: object, array, string, number, boolean or null. */
internal val JsonScalar: GraphQLScalarType =
    scalarType(
        name = "Json",
        description = "An arbitrary JSON value.",
        specifiedBy = "https://www.rfc-editor.org/rfc/rfc8259",
        coercing = JsonCoercing,
    )
