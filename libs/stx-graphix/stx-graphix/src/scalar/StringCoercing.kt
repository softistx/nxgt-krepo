package com.softistx.graphix.scalar

import com.softistx.graphix.message.MessageKeys
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import java.util.Locale
import kotlin.reflect.KClass

/**
 * The shape most of these scalars have: a Kotlin type on one side, a string on the wire, one
 * [encode] and one [decode]. `Instant`, `Uuid`, `LocalDate`, `Duration`, `Url` and the rest differ
 * only in those two lambdas, and writing the same twenty lines of `Coercing` for each is how the
 * twenty-first gets one of them subtly wrong.
 *
 * A `String` that arrives where [T] was expected is passed through on the way **out** — a resolver
 * that already formatted its value is not an error — and parsed on the way **in**, where the
 * value has to be the Kotlin type the schema promised.
 */
internal class StringCoercing<T : Any>(
    private val scalar: String,
    private val type: KClass<T>,
    private val encode: (T) -> String,
    private val decode: (String) -> T,
) : Coercing<T, String> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String {
        val coercion = Coercion(scalar, graphQLContext, locale)
        return when {
            type.isInstance(dataFetcherResult) -> encode(cast(dataFetcherResult))
            dataFetcherResult is String -> dataFetcherResult
            else -> coercion.cannotSerialize(dataFetcherResult)
        }
    }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): T {
        val coercion = Coercion(scalar, graphQLContext, locale)
        return when {
            type.isInstance(input) -> cast(input)
            input is String -> parse(input) { value, reason -> coercion.unreadable(value, reason) }
            else -> coercion.cannotRead(input)
        }
    }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): T {
        val coercion = Coercion(scalar, graphQLContext, locale)
        val literal = input as? StringValue ?: coercion.cannotAccept(input, MessageKeys.LITERAL_STRING)
        val text = literal.value ?: coercion.cannotAccept(input, MessageKeys.LITERAL_STRING)
        return parse(text) { value, reason -> coercion.unparseable(value, reason) }
    }

    private inline fun parse(
        text: String,
        failed: (String, String?) -> Nothing,
    ): T =
        try {
            decode(text)
        } catch (failure: Exception) {
            failed(text, failure.message)
        }

    @Suppress("UNCHECKED_CAST")
    private fun cast(value: Any): T = value as T
}

/**
 * Refuses a value from a [StringCoercing] decoder without a reason of its own. The value is
 * already in the message, and a hand-written English sentence there would be the one part of a
 * coercion error no catalogue can translate.
 */
internal fun refuse(): Nothing = throw IllegalArgumentException()
