package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import kotlin.reflect.KFunction
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions

/** One GraphQL root (`Query` or `Mutation`) and the data fetchers for its fields. */
internal data class Root(
    val type: GraphQLObjectType,
    val fetchers: List<Pair<FieldCoordinates, DataFetcher<*>>>,
)

/**
 * Builds [Root] from [instances]. Duplicate GraphQL field names across instances fail schema
 * build, naming the second class.
 */
internal fun root(
    name: String,
    kind: RootKind,
    instances: List<Any>,
    types: TypeMapper,
    fetcher: (Any, KFunction<*>) -> DataFetcher<*>,
): Root? {
    if (instances.isEmpty()) return null
    val fields = mutableListOf<GraphQLFieldDefinition>()
    val fetchers = mutableListOf<Pair<FieldCoordinates, DataFetcher<*>>>()
    val seen = mutableSetOf<String>()
    instances.forEach { instance ->
        instance.mappingFunctions(kind).forEach { function ->
            val fieldName = function.graphQLName(kind)
            if (!seen.add(fieldName)) {
                throw GraphixException("duplicate $kind field '$fieldName' on ${instance::class.qualifiedName}")
            }
            val output =
                types.output(
                    when (kind) {
                        RootKind.SUBSCRIPTION -> function.returnType.subscriptionElement()
                        else -> function.returnType.unwrapAsync()
                    },
                    function.isGraphQLId(),
                )
            function.requireArgumentAnnotations()
            fields +=
                fieldDefinition(
                    function,
                    fieldName,
                    output,
                    types,
                )
            fetchers += FieldCoordinates.coordinates(name, fieldName) to fetcher(instance, function)
        }
    }
    if (fields.isEmpty()) {
        throw GraphixException("no @$kind functions on ${instances.map { it::class.qualifiedName }}")
    }
    val type =
        GraphQLObjectType
            .newObject()
            .name(name)
            .fields(fields)
            .build()
    return Root(type, fetchers)
}

/** Registers every fetcher on [root] into this registry. */
internal fun GraphQLCodeRegistry.Builder.putAll(root: Root): GraphQLCodeRegistry.Builder {
    root.fetchers.forEach { (coordinates, fetcher) -> dataFetcher(coordinates, fetcher) }
    return this
}

internal fun List<Any>.rootFunctions(kind: RootKind): List<Pair<Any, KFunction<*>>> =
    flatMap { instance -> instance.mappingFunctions(kind).map { instance to it } }

internal fun Any.mappingFunctions(kind: RootKind): List<KFunction<*>> {
    val matches =
        this::class.memberFunctions.filter { function ->
            when (kind) {
                RootKind.QUERY -> function.hasAnnotation<QueryMapping>()
                RootKind.MUTATION -> function.hasAnnotation<MutationMapping>()
                RootKind.SUBSCRIPTION -> function.hasAnnotation<SubscriptionMapping>()
            }
        }
    if (matches.isEmpty()) {
        throw GraphixException("${this::class.qualifiedName} has no @$kind functions")
    }
    matches.forEach { function ->
        if (function.instanceParameter == null) {
            throw GraphixException("@$kind ${function.name} is not a member function")
        }
    }
    return matches
}
