package com.strange.graphql.schema

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

internal fun KFunction<*>.graphQLName(kind: RootKind): String {
    val fromKind =
        when (kind) {
            RootKind.QUERY -> findAnnotation<Query>()?.name.orEmpty()
            RootKind.MUTATION -> findAnnotation<Mutation>()?.name.orEmpty()
        }
    if (fromKind.isNotEmpty()) return fromKind
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    return name
}

internal fun KParameter.graphQLName(): String {
    findAnnotation<Argument>()?.name?.takeIf { it.isNotEmpty() }?.let { return it }
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    return name ?: throw IllegalStateException("a resolver parameter has no name; compile with parameter names retained")
}

internal fun KClass<*>.graphQLName(): String {
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    return simpleName ?: throw IllegalStateException("a GraphQL type has no name: $qualifiedName")
}

internal fun KAnnotatedElement.graphQLDescription(): String? = findAnnotation<GraphQLDescription>()?.value

internal fun KProperty<*>.isGraphQLIgnored(): Boolean = findAnnotation<GraphQLIgnore>() != null

internal enum class RootKind { QUERY, MUTATION }
