package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import graphql.language.Value
import graphql.parser.Parser
import kotlinx.serialization.SerialName
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

/**
 * GraphQL field name of a `@Serializable` property: [GraphQLName], then `@SerialName`, then the
 * Kotlin name.
 *
 * `@SerialName` counts because the SerialDescriptor **is** the type system here. A property renamed
 * for the wire is renamed in the schema too — otherwise the field would be absent from the object
 * type, and an input object would decode by a name the schema never advertised.
 */
internal fun KProperty<*>.graphQLPropertyName(): String =
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }
        ?: findAnnotation<SerialName>()?.value
        ?: name

internal fun KAnnotatedElement.graphQLDescription(): String? = findAnnotation<GraphQLDescription>()?.value

/** Deprecation reason, or `null` when the element is not deprecated. */
internal fun KAnnotatedElement.graphQLDeprecation(): String? = findAnnotation<GraphQLDeprecated>()?.reason

/** `true` when the element is annotated [GraphQLId], so its type is GraphQL's `ID`. */
internal fun KAnnotatedElement.isGraphQLId(): Boolean = findAnnotation<GraphQLId>() != null

/**
 * The GraphQL default literal on the element, parsed. A literal that does not parse is a schema
 * build failure naming [what].
 */
internal fun KAnnotatedElement.graphQLDefault(what: String): Value<*>? {
    val literal = findAnnotation<GraphQLDefault>()?.literal ?: return null
    return try {
        Parser.parseValue(literal)
    } catch (failure: Exception) {
        throw GraphixException("@GraphQLDefault on $what is not a GraphQL literal: '$literal'", failure)
    }
}

internal fun KProperty<*>.isGraphQLIgnored(): Boolean = findAnnotation<GraphQLIgnore>() != null

/** Query, Mutation or Subscription — which annotation and which root type to build. */
internal enum class RootKind { QUERY, MUTATION, SUBSCRIPTION }
