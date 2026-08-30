package com.strange.graphql.schema

import com.strange.graphql.GraphQlException
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

internal data class Root(
    val type: GraphQLObjectType,
    val fetchers: List<Pair<FieldCoordinates, DataFetcher<*>>>,
)

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
                throw GraphQlException("duplicate $kind field '$fieldName' on ${instance::class.qualifiedName}")
            }
            fields += field(function, fieldName, types)
            fetchers += FieldCoordinates.coordinates(name, fieldName) to fetcher(instance, function)
        }
    }
    if (fields.isEmpty()) {
        throw GraphQlException("no @$kind functions on ${instances.map { it::class.qualifiedName }}")
    }
    val type =
        GraphQLObjectType
            .newObject()
            .name(name)
            .fields(fields)
            .build()
    return Root(type, fetchers)
}

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
            }
        }
    if (matches.isEmpty()) {
        throw GraphQlException("${instance::class.qualifiedName} has no @$kind functions")
    }
    matches.forEach { function ->
        if (function.instanceParameter == null) {
            throw GraphQlException("@$kind ${function.name} is not a member function")
        }
    }
    return matches
}

private fun field(
    function: KFunction<*>,
    name: String,
    types: TypeMapper,
): GraphQLFieldDefinition {
    val builder =
        GraphQLFieldDefinition
            .newFieldDefinition()
            .name(name)
            .description(function.graphQLDescription())
            .type(types.output(function.returnType))
    function.valueParameters.filterNot { it.hasAnnotation<GraphQLContext>() }.forEach { parameter ->
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
