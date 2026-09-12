package com.softistx.graphix.scalar

import com.softistx.graphix.message.MessageKeys
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import java.math.BigDecimal
import java.util.Locale

/**
 * Money and anything else that must not be a `Double`. GraphQL's `Float` is IEEE 754 and loses
 * cents; this scalar keeps every digit the client sent.
 *
 * A `Double` on the way out is read through its **string** form, not `BigDecimal(double)` — the
 * constructor is exact about a value that was already approximate, and turns `0.1` into
 * `0.1000000000000000055511151231257827`.
 */
private object BigDecimalCoercing : Coercing<BigDecimal, BigDecimal> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): BigDecimal {
        val coercion = Coercion("BigDecimal", graphQLContext, locale)
        return decimal(dataFetcherResult) ?: coercion.cannotSerialize(dataFetcherResult)
    }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): BigDecimal {
        val coercion = Coercion("BigDecimal", graphQLContext, locale)
        if (input is String) return input.toBigDecimalOrNull() ?: coercion.unreadable(input)
        return decimal(input) ?: coercion.cannotRead(input)
    }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): BigDecimal {
        val coercion = Coercion("BigDecimal", graphQLContext, locale)
        return when (input) {
            is IntValue -> input.value.toBigDecimal()
            is FloatValue -> input.value
            is StringValue -> input.value?.toBigDecimalOrNull() ?: coercion.unparseable(input.value ?: "null")
            else -> coercion.cannotAccept(input, MessageKeys.LITERAL_FLOAT)
        }
    }

    private fun decimal(value: Any): BigDecimal? =
        when (value) {
            is BigDecimal -> value
            is Double, is Float -> value.toString().toBigDecimalOrNull()
            is Number -> value.toString().toBigDecimalOrNull()
            is String -> value.toBigDecimalOrNull()
            else -> null
        }
}

/** An exact decimal. The scalar to reach for when `Float` would round the answer. */
internal val BigDecimalScalar: GraphQLScalarType =
    scalarType(
        name = "BigDecimal",
        description = "A signed decimal of arbitrary precision.",
        coercing = BigDecimalCoercing,
    )
