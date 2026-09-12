package com.softistx.graphix.scalar

import com.softistx.graphix.message.MessageKeys
import graphql.schema.GraphQLScalarType

/**
 * The four `Int`s that say what they will accept. A range in the **type** is a range the schema
 * advertises and the engine enforces before a resolver runs; the same check written in the
 * resolver is a runtime error the client's code generator never saw.
 *
 * These have no Kotlin type of their own — there is none for "an Int above zero" — so they are
 * opt-in: `scalar PositiveInt` in SDL, or `scalars(Scalars.PositiveInt)` on the builder.
 */
private fun boundedInt(
    name: String,
    description: String,
    constraint: String,
    accepts: (Int) -> Boolean,
): GraphQLScalarType =
    scalarType(
        name = name,
        description = description,
        coercing =
            BoundedCoercing(
                name,
                constraint,
                IntegralCoercing(
                    name,
                    Int::class,
                    integralRange(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()),
                ) { it.toInt() },
                accepts,
            ),
    )

/** A 32-bit integer greater than zero. */
internal val PositiveIntScalar: GraphQLScalarType =
    boundedInt("PositiveInt", "A 32-bit integer greater than zero.", MessageKeys.RANGE_POSITIVE) { it > 0 }

/** A 32-bit integer less than zero. */
internal val NegativeIntScalar: GraphQLScalarType =
    boundedInt("NegativeInt", "A 32-bit integer less than zero.", MessageKeys.RANGE_NEGATIVE) { it < 0 }

/** A 32-bit integer of zero or less. */
internal val NonPositiveIntScalar: GraphQLScalarType =
    boundedInt("NonPositiveInt", "A 32-bit integer of zero or less.", MessageKeys.RANGE_NON_POSITIVE) { it <= 0 }

/** A 32-bit integer of zero or more — a count, a page size, a quantity. */
internal val NonNegativeIntScalar: GraphQLScalarType =
    boundedInt("NonNegativeInt", "A 32-bit integer of zero or more.", MessageKeys.RANGE_NON_NEGATIVE) { it >= 0 }
