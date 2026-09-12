package com.softistx.graphix.scalar

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.Value
import graphql.schema.Coercing
import java.util.Locale

/**
 * A scalar that is another scalar plus a predicate: `PositiveInt` is `Int` that refuses zero and
 * below. The range is checked on the way **out** too — a resolver returning `-1` from a field
 * typed `PositiveInt` has broken the contract the schema advertised, and a client that trusted it
 * would rather hear so than receive the value.
 *
 * [constraint] is a `MessageKeys.RANGE_*` key, so what the range is called is translated with
 * everything else rather than concatenated in English here.
 */
internal class BoundedCoercing<T : Any>(
    private val scalar: String,
    private val constraint: String,
    private val base: Coercing<T, T>,
    private val accepts: (T) -> Boolean,
) : Coercing<T, T> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
        // graphql-java declares every Coercing method @Nullable. The bases here are this module's
        // own, and every one of them either returns a value or throws.
    ): T = check(base.serialize(dataFetcherResult, graphQLContext, locale)!!, graphQLContext, locale, CoercionPhase.Serialize)

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): T = check(base.parseValue(input, graphQLContext, locale)!!, graphQLContext, locale, CoercionPhase.Value)

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): T = check(base.parseLiteral(input, variables, graphQLContext, locale)!!, graphQLContext, locale, CoercionPhase.Literal)

    private fun check(
        value: T,
        context: GraphQLContext,
        locale: Locale,
        phase: CoercionPhase,
    ): T = if (accepts(value)) value else Coercion(scalar, context, locale).outOfRange(value, constraint, phase = phase)
}
