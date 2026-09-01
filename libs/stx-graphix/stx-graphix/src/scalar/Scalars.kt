package com.softistx.graphix.scalar

import graphql.schema.GraphQLScalarType

/**
 * GraphQL's spec scalars plus the three Kotlin types this stack already treats as primitives.
 * Instant and Uuid travel as strings; Long is a scalar of its own because GraphQL Int is 32-bit.
 */
object Scalars {
    /** GraphQL `Int` is 32-bit. Kotlin `Long` is this scalar, serialized as an integer. */
    val Long: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Long")
            .description("A 64-bit signed integer.")
            .coercing(LongCoercing)
            .build()

    /** `kotlin.time.Instant` as an ISO-8601 string. */
    val Instant: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Instant")
            .description("An instant on the UTC timeline, ISO-8601.")
            .coercing(InstantCoercing)
            .build()

    /** `kotlin.uuid.Uuid` as the canonical hyphenated string. */
    val Uuid: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Uuid")
            .description("A UUID, canonical string form.")
            .coercing(UuidCoercing)
            .build()
}
