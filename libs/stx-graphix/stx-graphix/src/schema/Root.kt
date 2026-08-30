package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters

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
        functions(instance, kind).forEach { function ->
            val fieldName = function.graphQLName(kind)
            if (!seen.add(fieldName)) {
                throw GraphixException("duplicate $kind field '$fieldName' on ${instance::class.qualifiedName}")
            }
            fields += field(function, fieldName, types, kind)
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

private fun functions(
    instance: Any,
    kind: RootKind,
): List<KFunction<*>> {
    val matches =
        instance::class.memberFunctions.filter { function ->
            when (kind) {
                RootKind.QUERY -> function.hasAnnotation<Query>()
                RootKind.MUTATION -> function.hasAnnotation<Mutation>()
                RootKind.SUBSCRIPTION -> function.hasAnnotation<Subscription>()
            }
        }
    if (matches.isEmpty()) {
        throw GraphixException("${instance::class.qualifiedName} has no @$kind functions")
    }
    matches.forEach { function ->
        if (function.instanceParameter == null) {
            throw GraphixException("@$kind ${function.name} is not a member function")
        }
    }
    return matches
}

private fun field(
    function: KFunction<*>,
    name: String,
    types: TypeMapper,
    kind: RootKind,
): GraphQLFieldDefinition {
    val builder =
        GraphQLFieldDefinition
            .newFieldDefinition()
            .name(name)
            .description(function.graphQLDescription())
            .type(types.output(if (kind == RootKind.SUBSCRIPTION) function.returnType.subscriptionElement() else function.returnType))
    function.valueParameters.filterNot { it.hasAnnotation<GraphQLContext>() }.forEach { parameter ->
        // A Kotlin default is still GraphQL NonNull unless unwrapped: graphql-java has no defaults.
        val argumentType =
            types.input(parameter.type).let { type ->
                if (parameter.isOptional && type is graphql.schema.GraphQLNonNull) {
                    type.wrappedType as graphql.schema.GraphQLInputType
                } else {
                    type
                }
            }
        builder.argument(
            GraphQLArgument
                .newArgument()
                .name(parameter.graphQLName())
                .description(parameter.findAnnotation<GraphQLDescription>()?.value)
                .type(argumentType)
                .build(),
        )
    }
    return builder.build()
}
