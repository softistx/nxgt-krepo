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
annotation class QueryMapping(
    /** GraphQL field name. Empty uses [GraphQLName] or the Kotlin name. */
    val name: String = "",
)

/**
 * Marks a function as a field on the Mutation root. Naming follows [QueryMapping]. The instance
 * is the one passed to [com.strange.graphix.GraphixBuilder.mutation].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MutationMapping(
    /** GraphQL field name. Empty uses [GraphQLName] or the Kotlin name. */
    val name: String = "",
)

/**
 * Marks a function as a field on the Subscription root. Naming follows [QueryMapping]. The
 * instance is the one passed to [com.strange.graphix.GraphixBuilder.subscription].
 *
 * The return type must be `Flow<T>` or a reactive-streams / JDK `Publisher<T>`. `T` is the
 * GraphQL field type. Collect with [com.strange.graphix.Graphix.subscribe], not
 * [com.strange.graphix.Graphix.execute].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SubscriptionMapping(
    /** GraphQL field name. Empty uses [GraphQLName] or the Kotlin name. */
    val name: String = "",
)

/**
 * Extra field on a `@Serializable` type, not a root. The instance is passed to
 * [com.strange.graphix.GraphixBuilder.type].
 *
 * [typeName] defaults to the simple name of the first argument's type. [field] defaults to
 * the Kotlin function name. The first parameter that is not DFE / `@GraphQLContext` is the
 * parent (`env.source`). GraphQL arguments are `[Argument]` parameters.
 *
 * A field is either this or [BatchMapping], not both.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SchemaMapping(
    val typeName: String = "",
    val field: String = "",
)

/**
 * Batched extra field on a `@Serializable` type. graphql-java DataLoader is underneath;
 * Graphix registers the field — no [SchemaMapping] on the same field.
 *
 * The first parameter is the parents of this level (`books: List<Book>`). GraphQL arguments
 * are `[Argument]` parameters — DataLoader keys include those values, so aliases with
 * different arguments do not share a cached row. An optional
 * [graphql.schema.DataFetchingEnvironment] is this field's DFE. Return `Map<Book, T>` or
 * `List<T>` in key order. [typeName] defaults to the list element's simple name, [field] to
 * the Kotlin function name.
 *
 * ```kotlin
 * @BatchMapping
 * suspend fun author(books: List<Book>): Map<Book, Author>
 *
 * @BatchMapping
 * suspend fun snippets(books: List<Book>, @Argument limit: Int): Map<Book, List<String>>
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BatchMapping(
    val typeName: String = "",
    val field: String = "",
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
 * Injects a value into a resolver parameter by `KClass`.
 *
 * [graphql.schema.DataFetchingEnvironment] is recognized by type and does not need this
 * annotation. Other types are looked up in [com.strange.graphix.Graphix.execute]'s map.
 * Missing → [com.strange.graphix.GraphixException].
 *
 * Not a GraphQL argument, and not how a Spring bean is reached — those stay on the controller
 * constructor.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLContext

/**
 * Marks a GraphQL argument. Required on every resolver parameter that is an argument.
 * The parent source, this field's [graphql.schema.DataFetchingEnvironment], and
 * `@GraphQLContext` do not take it. An input object's fields do not take it either — the
 * object is already the argument.
 *
 * [name] defaults to the Kotlin parameter name (or `@GraphQLName`). Compile with parameter
 * names retained or this has nothing to read.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Argument(
    /** GraphQL argument name. Empty uses [GraphQLName] or the Kotlin parameter name. */
    val name: String = "",
)

/**
 * Applies a named field directive registered with [com.strange.graphix.fieldDirective].
 * On an SDL schema, `@name` on the field is enough — this is for the annotation-derived schema.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Directive(
    val name: String,
)

/**
 * Forces a `sealed` type to become a GraphQL `union` rather than an `interface`.
 *
 * Without it, a sealed type that declares properties every subclass carries becomes an
 * `interface`, and one that declares none becomes a `union`. There is no annotation for the
 * other direction: a GraphQL interface needs at least one field, so a sealed type with no shared
 * properties can only be a union.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphQLUnion
