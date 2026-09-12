package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import kotlinx.datetime.LocalDateTime

/**
 * A date and a time with **no zone**. It does not name a moment: two clients in two zones read
 * one `LocalDateTime` as two different instants, which is exactly why [InstantScalar] exists.
 */
internal val LocalDateTimeScalar: GraphQLScalarType =
    scalarType(
        name = "LocalDateTime",
        description = "A date and time without a time zone, ISO-8601: 2026-09-02T14:30:05.",
        coercing =
            StringCoercing("LocalDateTime", LocalDateTime::class, { it.toString() }, { LocalDateTime.parse(it) }),
    )
