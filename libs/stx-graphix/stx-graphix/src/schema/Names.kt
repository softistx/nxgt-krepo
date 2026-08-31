package com.strange.graphix.schema

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

/** GraphQL field name: `@QueryMapping(name)` / `@MutationMapping(name)`, then `@GraphQLName`, then the Kotlin name. */
internal fun KFunction<*>.graphQLName(kind: RootKind): String {
    val fromKind =
        when (kind) {
            RootKind.QUERY -> findAnnotation<QueryMapping>()?.name.orEmpty()
            RootKind.MUTATION -> findAnnotation<MutationMapping>()?.name.orEmpty()
            RootKind.SUBSCRIPTION -> findAnnotation<SubscriptionMapping>()?.name.orEmpty()
        }
    if (fromKind.isNotEmpty()) return fromKind
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    return name
}

/**
 * GraphQL argument name: `@Argument`, then `@GraphQLName`, then the Kotlin parameter name.
 * Parameter names must be retained at compile time — otherwise this throws.
 */
internal fun KParameter.graphQLName(): String {
    findAnnotation<Argument>()?.name?.takeIf { it.isNotEmpty() }?.let { return it }
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    return name ?: throw IllegalStateException("a resolver parameter has no name; compile with parameter names retained")
}

/** GraphQL type name: `@GraphQLName`, then the Kotlin simple name. */
internal fun KClass<*>.graphQLName(): String =
    graphQLNameOrNull() ?: throw IllegalStateException("a GraphQL type has no name: $qualifiedName")

/**
 * Same as [graphQLName], but `null` for a type that has no name at all — an anonymous or local
 * class. Type resolution runs per value at execute time, where a throw is a crashed operation
 * rather than a schema-build failure.
 */
internal fun KClass<*>.graphQLNameOrNull(): String? {
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    return simpleName
}

internal fun KAnnotatedElement.graphQLDescription(): String? = findAnnotation<GraphQLDescription>()?.value

internal fun KProperty<*>.isGraphQLIgnored(): Boolean = findAnnotation<GraphQLIgnore>() != null

/** Query, Mutation or Subscription — which annotation and which root type to build. */
internal enum class RootKind { QUERY, MUTATION, SUBSCRIPTION }
