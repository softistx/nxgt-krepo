package com.softistx.graphix.scalar

import com.softistx.graphix.message.MessageKeys
import graphql.schema.GraphQLScalarType

/**
 * The four `Float`s that say what they will accept. Same argument as the bounded ints: the range
 * belongs in the type, where a client's generated code can see it.
 *
 * Opt-in like them — `scalar PositiveFloat` in SDL, or `scalars(Scalars.PositiveFloat)`.
 */
private fun boundedFloat(
    name: String,
    description: String,
    constraint: String,
    accepts: (Double) -> Boolean,
): GraphQLScalarType =
    scalarType(
        name = name,
        description = description,
        coercing = BoundedCoercing(name, constraint, DoubleCoercing, accepts),
    )

/** A `Float` greater than zero. */
internal val PositiveFloatScalar: GraphQLScalarType =
    boundedFloat("PositiveFloat", "A double-precision float greater than zero.", MessageKeys.RANGE_POSITIVE) { it > 0.0 }

/** A `Float` less than zero. */
internal val NegativeFloatScalar: GraphQLScalarType =
    boundedFloat("NegativeFloat", "A double-precision float less than zero.", MessageKeys.RANGE_NEGATIVE) { it < 0.0 }

/** A `Float` of zero or less. */
internal val NonPositiveFloatScalar: GraphQLScalarType =
    boundedFloat("NonPositiveFloat", "A double-precision float of zero or less.", MessageKeys.RANGE_NON_POSITIVE) { it <= 0.0 }

/** A `Float` of zero or more. */
internal val NonNegativeFloatScalar: GraphQLScalarType =
    boundedFloat("NonNegativeFloat", "A double-precision float of zero or more.", MessageKeys.RANGE_NON_NEGATIVE) { it >= 0.0 }
