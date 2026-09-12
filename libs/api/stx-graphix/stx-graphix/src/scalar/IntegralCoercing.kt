package com.softistx.graphix.scalar

import com.softistx.graphix.message.MessageKeys
import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import kotlin.reflect.KClass

/**
 * A whole number on the wire. Every integral scalar here is this class plus a width: `Long`,
 * `Short` and `Byte` carry a [range] and `BigInteger` does not.
 *
 * The conversion goes through [BigInteger] deliberately. A GraphQL `IntValue` already holds one —
 * the parser does not narrow — so checking the range before narrowing is what turns an
 * out-of-range literal into a coercion error naming the window instead of a silent wrap.
 *
 * A `Double` that happens to be whole is accepted; one with a fraction is not. Truncating it would
 * be the one coercion the caller cannot see happen.
 */
internal class IntegralCoercing<T : Any>(
    private val scalar: String,
    private val type: KClass<T>,
    private val range: ClosedRange<BigInteger>?,
    private val convert: (BigInteger) -> T,
) : Coercing<T, T> {
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): T {
        val coercion = Coercion(scalar, graphQLContext, locale)
        val number = integral(dataFetcherResult) ?: coercion.cannotSerialize(dataFetcherResult)
        return narrow(number, coercion, CoercionPhase.Serialize)
    }

    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): T {
        val coercion = Coercion(scalar, graphQLContext, locale)
        if (type.isInstance(input)) {
            @Suppress("UNCHECKED_CAST")
            return input as T
        }
        val number =
            when (input) {
                is String -> input.toBigIntegerOrNull() ?: coercion.unreadable(input)
                else -> integral(input) ?: coercion.cannotRead(input)
            }
        return narrow(number, coercion, CoercionPhase.Value)
    }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): T {
        val coercion = Coercion(scalar, graphQLContext, locale)
        val number =
            when (input) {
                is IntValue -> input.value

                // A 64-bit value is still an integer literal; a quoted one is how clients that
                // cannot hold it in a JSON number send it.
                is StringValue -> input.value?.toBigIntegerOrNull() ?: coercion.unparseable(input.value ?: "null")

                else -> coercion.cannotAccept(input, MessageKeys.LITERAL_INT)
            }
        return narrow(number, coercion, CoercionPhase.Literal)
    }

    private fun narrow(
        number: BigInteger,
        coercion: Coercion,
        phase: CoercionPhase,
    ): T {
        val window = range
        if (window != null && number !in window) {
            coercion.outOfRange(
                number,
                MessageKeys.RANGE_BETWEEN,
                mapOf("min" to window.start, "max" to window.endInclusive),
                phase,
            )
        }
        return convert(number)
    }

    /** Whole numbers only — a fractional `Double` is a failure, not a truncation. */
    private fun integral(value: Any): BigInteger? =
        when (value) {
            is BigInteger -> value
            is Long, is Int, is Short, is Byte -> BigInteger.valueOf((value as Number).toLong())
            is BigDecimal -> value.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toBigIntegerExact()
            is Double -> value.takeIf { it % 1.0 == 0.0 }?.let { BigDecimal(it).toBigIntegerExact() }
            is Float -> value.takeIf { it % 1.0f == 0.0f }?.let { BigDecimal(it.toDouble()).toBigIntegerExact() }
            else -> null
        }
}

/** `Long.MIN_VALUE..Long.MAX_VALUE` and friends, as the [BigInteger] window [IntegralCoercing] checks. */
internal fun integralRange(
    min: kotlin.Long,
    max: kotlin.Long,
): ClosedRange<BigInteger> = BigInteger.valueOf(min)..BigInteger.valueOf(max)
