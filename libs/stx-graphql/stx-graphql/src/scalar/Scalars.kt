package com.strange.graphql.scalar

import graphql.schema.GraphQLScalarType

/**
 * GraphQL's spec scalars plus the three Kotlin types this stack already treats as primitives.
 * Instant and Uuid travel as strings; Long is a scalar of its own because GraphQL Int is 32-bit.
 */
object Scalars {
    val Long: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Long")
            .description("A 64-bit signed integer.")
            .coercing(LongCoercing)
            .build()

    val Instant: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Instant")
            .description("An instant on the UTC timeline, ISO-8601.")
            .coercing(InstantCoercing)
            .build()

    val Uuid: GraphQLScalarType =
        GraphQLScalarType
            .newScalar()
            .name("Uuid")
            .description("A UUID, canonical string form.")
            .coercing(UuidCoercing)
            .build()
}
