package com.softistx.graphix.scalar

import com.softistx.graphix.message.MessageKeys
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import java.util.Locale

/**
 * GraphQL's `Float` as a Kotlin `Double`. Used on its own by nothing — a plain `Double` field is
 * already GraphQL `Float` — and by the bounded float scalars for everything except their range.
 *
 * `NaN` and the infinities are refused. JSON has no way to write them, so serializing one
 * produces a document the client's own parser rejects, several layers from the resolver that
 * divided by zero.
 */
internal object DoubleCoercing : Coercing<Double, Double> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Double {
        val coercion = Coercion("Float", graphQLContext, locale)
        val value = decimal(dataFetcherResult) ?: coercion.cannotSerialize(dataFetcherResult)
        return finite(value) { coercion.cannotSerialize(dataFetcherResult) }
    }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Double {
        val coercion = Coercion("Float", graphQLContext, locale)
        val value =
            when (input) {
                is String -> input.toDoubleOrNull() ?: coercion.unreadable(input)
                else -> decimal(input) ?: coercion.cannotRead(input)
            }
        return finite(value) { coercion.unreadable(input) }
    }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Double {
        val coercion = Coercion("Float", graphQLContext, locale)
        val value =
            when (input) {
                is FloatValue -> input.value.toDouble()
                is IntValue -> input.value.toDouble()
                is StringValue -> input.value?.toDoubleOrNull() ?: coercion.unparseable(input.value ?: "null")
                else -> coercion.cannotAccept(input, MessageKeys.LITERAL_FLOAT)
            }
        return finite(value) { coercion.unparseable(value) }
    }

    private inline fun finite(
        value: Double,
        refused: () -> Nothing,
    ): Double = if (value.isFinite()) value else refused()

    private fun decimal(value: Any): Double? =
        when (value) {
            is Double -> value
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
}
