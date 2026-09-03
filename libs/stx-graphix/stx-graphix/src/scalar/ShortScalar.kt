package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType

/** A Kotlin `Short`. GraphQL's `Int` would take it, but then the schema stops saying so. */
internal val ShortScalar: GraphQLScalarType =
    scalarType(
        name = "Short",
        description = "A 16-bit signed integer.",
        coercing =
            IntegralCoercing(
                "Short",
                Short::class,
                integralRange(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()),
            ) { it.toShort() },
    )
