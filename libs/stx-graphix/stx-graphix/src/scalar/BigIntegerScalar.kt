package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import java.math.BigInteger

/** An integer of any width. Unbounded, so nothing here checks a range — there is none to check. */
internal val BigIntegerScalar: GraphQLScalarType =
    scalarType(
        name = "BigInteger",
        description = "An integer of arbitrary precision.",
        coercing = IntegralCoercing("BigInteger", BigInteger::class, range = null) { it },
    )
