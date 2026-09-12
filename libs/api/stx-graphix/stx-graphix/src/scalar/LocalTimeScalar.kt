package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import kotlinx.datetime.LocalTime

/** A time of day with no date and no zone — `14:30`, `14:30:05.250`. */
internal val LocalTimeScalar: GraphQLScalarType =
    scalarType(
        name = "LocalTime",
        description = "A time without a date or time zone, ISO-8601: 14:30:05.",
        coercing = StringCoercing("LocalTime", LocalTime::class, { it.toString() }, { LocalTime.parse(it) }),
    )
