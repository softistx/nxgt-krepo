package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType

/**
 * A single character, as a one-character string. A longer string is a coercion error and not a
 * truncation — silently keeping the first character is how a grade `A+` becomes an `A`.
 */
internal val CharScalar: GraphQLScalarType =
    scalarType(
        name = "Char",
        description = "A single character.",
        coercing =
            StringCoercing("Char", Char::class, { it.toString() }, { text ->
                text.singleOrNull() ?: refuse()
            }),
    )
