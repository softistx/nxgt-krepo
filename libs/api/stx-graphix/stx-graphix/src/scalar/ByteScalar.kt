package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType

/** A Kotlin `Byte`, as a number. Not a byte *string* — base64 blobs are a scalar of your own. */
internal val ByteScalar: GraphQLScalarType =
    scalarType(
        name = "Byte",
        description = "An 8-bit signed integer.",
        coercing =
            IntegralCoercing(
                "Byte",
                Byte::class,
                integralRange(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()),
            ) { it.toByte() },
    )
