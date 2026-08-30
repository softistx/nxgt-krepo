package com.strange.graphix.schema

/**
 * Marks a function as a field on the Query root.
 *
 * The function lives on an instance passed to [com.strange.graphix.GraphixBuilder.query]. A
 * Spring bean, a store, a client: those are that instance's constructor, not [GraphQLContext].
 *
 * The GraphQL name is [name] if set, otherwise [GraphQLName] on the function, otherwise the
 * Kotlin name.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Query(
    /** GraphQL field name. Empty uses [GraphQLName] or the Kotlin name. */
    val name: String = "",
)

/**
 * Marks a function as a field on the Mutation root. Naming follows [Query]. The instance is
 * the one passed to [com.strange.graphix.GraphixBuilder.mutation].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Mutation(
    /** GraphQL field name. Empty uses [GraphQLName] or the Kotlin name. */
    val name: String = "",
)

/** Overrides the GraphQL name of a type, field, or argument. Empty [value] is ignored. */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLName(
    /** GraphQL name. Empty is ignored, same as omitting the annotation. */
    val value: String,
)

/** GraphQL description on a type, field, or argument. */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLDescription(
    /** Text that becomes the GraphQL `description` on this element. */
    val value: String,
)

/** Drops a `@Serializable` property from the GraphQL type. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLIgnore

/**
 * Injects a **per-operation** value into a resolver parameter. Looked up by the parameter's
 * `KClass` in [com.strange.graphix.Graphix.execute]'s `context` map. Missing → [com.strange.graphix.GraphixException].
 *
 * Not a GraphQL argument, and not how a Spring bean is reached — those stay on the controller
 * constructor. HTTP plugins currently put only the operation `CoroutineScope` in that map.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLContext

/**
 * Overrides the GraphQL argument name. The Kotlin parameter name is the default; compile with
 * parameter names retained or this has nothing to read.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Argument(
    /** GraphQL argument name. Empty uses [GraphQLName] or the Kotlin parameter name. */
    val name: String = "",
)
