package com.softistx.graphix.scalar

import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import kotlin.reflect.KClass

/**
 * One built-in scalar declaration. A scalar's own file calls this; nothing else builds a
 * [GraphQLScalarType] by hand, so `name`, `description` and `@specifiedBy` stay one shape.
 *
 * [specifiedBy] is the URL of what the string actually holds — RFC 3339 for a timestamp, RFC 4122
 * for a UUID. A client generator reads it to pick a type; a human reads it to stop guessing.
 */
internal fun scalarType(
    name: String,
    description: String,
    coercing: Coercing<*, *>,
    specifiedBy: String? = null,
): GraphQLScalarType =
    GraphQLScalarType
        .newScalar()
        .name(name)
        .description(description)
        .specifiedByUrl(specifiedBy)
        .coercing(coercing)
        .build()

/**
 * How a built-in scalar is reached from Kotlin. [kotlinTypes] is what a field of that class maps
 * to on the annotation path; [serialNames] is the same answer for the `SerialDescriptor` walk,
 * which is what a property inside a `@Serializable` class goes through.
 *
 * Both empty means the scalar is **opt-in**: it exists, and a schema uses it by declaring it in
 * SDL or by registering it on the builder. `PositiveInt` is that — there is no Kotlin type for
 * "an Int above zero", and inferring one from a field name is not a thing this library does.
 */
internal class ScalarBinding(
    val type: GraphQLScalarType,
    val kotlinTypes: List<KClass<*>> = emptyList(),
    val serialNames: List<String> = emptyList(),
)
