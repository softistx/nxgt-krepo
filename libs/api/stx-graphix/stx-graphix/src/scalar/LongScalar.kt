package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType

/**
 * GraphQL's `Int` is 32-bit and a Kotlin `Long` is not, so `Long` is a scalar of its own rather
 * than a field that silently overflows above two billion. It stays a JSON **number**; a client
 * that cannot hold one may send it quoted.
 */
internal val LongScalar: GraphQLScalarType =
    scalarType(
        name = "Long",
        description = "A 64-bit signed integer.",
        coercing =
            IntegralCoercing("Long", Long::class, integralRange(Long.MIN_VALUE, Long.MAX_VALUE)) { it.toLong() },
    )
