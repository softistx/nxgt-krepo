package com.strange.graphix.scalar

import com.strange.graphix.GraphixBuilder
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
 * [GraphQLContext] as receiver — the same bag `@GraphQLContext` reads.
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
 */
fun graphQLScalar(
    name: String,
    description: String = "",
    block: ScalarSpec.() -> Unit,
): GraphQLScalarType {
    val spec = ScalarSpec().apply(block)
    return GraphQLScalarType
        .newScalar()
        .name(name)
        .description(description.ifEmpty { null })
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

/** Registers [type]. [kotlinType] is how an annotated field of that class becomes this scalar. */
fun GraphixBuilder.scalar(
    type: GraphQLScalarType,
    kotlinType: KClass<*>? = null,
) {
    addScalar(type, kotlinType)
}

/** Declares a scalar in place. Same [kotlinType] rule as [scalar]. */
fun GraphixBuilder.scalar(
    name: String,
    description: String = "",
    kotlinType: KClass<*>? = null,
    block: ScalarSpec.() -> Unit,
) {
    scalar(graphQLScalar(name, description, block), kotlinType)
}
