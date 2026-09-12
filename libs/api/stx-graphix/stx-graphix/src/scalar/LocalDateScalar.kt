package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import kotlinx.datetime.LocalDate

/**
 * A calendar date with no time and no zone — `2026-09-02`. The same type the OpenAPI generator
 * emits for `format: date`, so a model shared between the REST and GraphQL halves stays one type.
 */
internal val LocalDateScalar: GraphQLScalarType =
    scalarType(
        name = "LocalDate",
        description = "A date without a time zone, ISO-8601: 2026-09-02.",
        coercing = StringCoercing("LocalDate", LocalDate::class, { it.toString() }, { LocalDate.parse(it) }),
    )
