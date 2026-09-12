package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import kotlin.time.Duration

/**
 * `kotlin.time.Duration` in ISO-8601 form — `PT1H30M`, not the `1h 30m` that `toString` gives.
 * `toString` is for a log line; a wire format is parsed by something that is not Kotlin.
 */
internal val DurationScalar: GraphQLScalarType =
    scalarType(
        name = "Duration",
        description = "A length of time, ISO-8601: PT1H30M.",
        coercing =
            StringCoercing("Duration", Duration::class, { it.toIsoString() }, { Duration.parseIsoString(it) }),
    )
