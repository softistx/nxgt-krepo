package com.softistx.graphix.scalar

import com.softistx.graphix.GraphixBuilder
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import java.util.Locale
import kotlin.reflect.KClass

/**
 * A custom scalar. [serialize], [parseValue] and [parseLiteral] run with the operation's
 * [GraphQLContext] as receiver — the same bag a context parameter reads.
 */
class ScalarSpec {
    internal var serializeFn: GraphQLContext.(Any) -> Any = { it }
    internal var parseValueFn: GraphQLContext.(Any) -> Any = { it }
    internal var parseLiteralFn: GraphQLContext.(Value<*>) -> Any = {
        throw CoercingParseLiteralException("this scalar does not parse literals")
    }

    fun serialize(block: GraphQLContext.(value: Any) -> Any) {
        serializeFn = block
    }

    fun parseValue(block: GraphQLContext.(input: Any) -> Any) {
        parseValueFn = block
    }

    fun parseLiteral(block: GraphQLContext.(input: Value<*>) -> Any) {
        parseLiteralFn = block
    }
}

/**
 * Builds a [GraphQLScalarType] from [ScalarSpec] lambdas. Register it with
 * [GraphixBuilder.scalar].
 *
 * [specifiedBy] is the URL of the scalar's specification — GraphQL's `@specifiedBy`, which tells
 * a client generator what the string actually holds.
 */
fun graphQLScalar(
    name: String,
    description: String = "",
    specifiedBy: String = "",
    block: ScalarSpec.() -> Unit,
): GraphQLScalarType {
    val spec = ScalarSpec().apply(block)
    return GraphQLScalarType
        .newScalar()
        .name(name)
        .description(description.ifEmpty { null })
        .specifiedByUrl(specifiedBy.ifEmpty { null })
        .coercing(
            object : Coercing<Any, Any> {
                override fun serialize(
                    dataFetcherResult: Any,
                    graphQLContext: GraphQLContext,
                    locale: Locale,
                ): Any =
                    try {
                        graphQLContext.(spec.serializeFn)(dataFetcherResult)
                    } catch (failure: CoercingSerializeException) {
                        throw failure
                    } catch (failure: Exception) {
                        throw CoercingSerializeException(failure.message, failure)
                    }

                override fun parseValue(
                    input: Any,
                    graphQLContext: GraphQLContext,
                    locale: Locale,
                ): Any =
                    try {
                        graphQLContext.(spec.parseValueFn)(input)
                    } catch (failure: CoercingParseValueException) {
                        throw failure
                    } catch (failure: Exception) {
                        throw CoercingParseValueException(failure.message, failure)
                    }

                override fun parseLiteral(
                    input: Value<*>,
                    variables: CoercedVariables,
                    graphQLContext: GraphQLContext,
                    locale: Locale,
                ): Any =
                    try {
                        graphQLContext.(spec.parseLiteralFn)(input)
                    } catch (failure: CoercingParseLiteralException) {
                        throw failure
                    } catch (failure: Exception) {
                        throw CoercingParseLiteralException(failure.message, failure)
                    }
            },
        ).build()
}

/**
 * Registers the built-in scalars that are not reached by a Kotlin type — the bounded ones:
 *
 * ```kotlin
 * Graphix {
 *     scalars(Scalars.PositiveInt, Scalars.NonNegativeInt)
 *     resolvers(CatalogQueries(store))
 * }
 * ```
 *
 * A scalar a field already uses needs no call: `Instant` arrives with the first `Instant` field.
 */
fun GraphixBuilder.scalars(vararg types: GraphQLScalarType) {
    types.forEach { scalar(it) }
}

/** Registers [type]. [kotlinType] is how an annotated field of that class becomes this scalar. */
fun GraphixBuilder.scalar(
    type: GraphQLScalarType,
    kotlinType: KClass<*>? = null,
) {
    addScalar(type, kotlinType)
}

/** Declares a scalar in place. Same [kotlinType] rule as [scalar], same [specifiedBy] as [graphQLScalar]. */
fun GraphixBuilder.scalar(
    name: String,
    description: String = "",
    kotlinType: KClass<*>? = null,
    specifiedBy: String = "",
    block: ScalarSpec.() -> Unit,
) {
    scalar(graphQLScalar(name, description, specifiedBy, block), kotlinType)
}
