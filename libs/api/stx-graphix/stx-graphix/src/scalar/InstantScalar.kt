package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType
import kotlin.time.Instant

/** `kotlin.time.Instant` as an ISO-8601 string. Not `java.time` — this stack's clock is Kotlin's. */
internal val InstantScalar: GraphQLScalarType =
    scalarType(
        name = "Instant",
        description = "An instant on the UTC timeline, ISO-8601.",
        specifiedBy = "https://www.rfc-editor.org/rfc/rfc3339",
        coercing = StringCoercing("Instant", Instant::class, { it.toString() }, { Instant.parse(it) }),
    )
