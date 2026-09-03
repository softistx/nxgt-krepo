package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
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
 * Builds [Root] from the [instances] that carry `@`[kind]` ` functions — every registered instance
 * is offered to every root, and the annotation decides which one it lands on. `null` when none of
 * them has a function for this root, which is what a schema with no mutations looks like.
 *
 * Duplicate GraphQL field names across instances fail schema build, naming the second class.
 */
internal fun root(
    name: String,
    kind: RootKind,
    instances: List<Any>,
    types: TypeMapper,
    fetcher: (Any, KFunction<*>) -> DataFetcher<*>,
): Root? {
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
    if (fields.isEmpty()) return null
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

/**
 * This instance's functions for [kind], which may be none: one instance is offered to all three
 * roots and a class of queries has no mutations. What is *not* allowed is an instance with no
 * mapping of any kind, and [carriesMappings] is where that is refused — at registration, naming
 * the class, rather than three roots later.
 */
internal fun Any.mappingFunctions(kind: RootKind): List<KFunction<*>> {
    val matches =
        this::class.memberFunctions.filter { function ->
            when (kind) {
                RootKind.QUERY -> function.hasAnnotation<QueryMapping>()
                RootKind.MUTATION -> function.hasAnnotation<MutationMapping>()
                RootKind.SUBSCRIPTION -> function.hasAnnotation<SubscriptionMapping>()
            }
        }
    matches.forEach { function ->
        if (function.instanceParameter == null) {
            throw GraphixException("@$kind ${function.name} is not a member function")
        }
    }
    return matches
}

/**
 * Whether this instance has anything Graphix would read off it: a root mapping, a type-field
 * mapping, or a declared `dataLoader { }`. A class with none was a mistake the old
 * `query(NotAQuery())` caught by accident, and one registration call has to catch it on purpose.
 */
internal fun Any.carriesMappings(): Boolean {
    val functions = this::class.memberFunctions
    return functions.any {
        it.hasAnnotation<QueryMapping>() ||
            it.hasAnnotation<MutationMapping>() ||
            it.hasAnnotation<SubscriptionMapping>() ||
            it.hasAnnotation<SchemaMapping>() ||
            it.hasAnnotation<BatchMapping>()
    } ||
        declaresLoader()
}
