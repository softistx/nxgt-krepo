package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import com.strange.graphix.execute.RegisteredLoader
import com.strange.graphix.execute.batchFieldFetcher
import com.strange.graphix.execute.bindArguments
import com.strange.graphix.execute.resolverFetcher
import com.strange.graphix.execute.subscriptionFetcher
import com.strange.graphix.scalar.Scalars
import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeRuntimeWiring
import kotlinx.serialization.json.Json

/**
 * graphql-java executable schema from SDL files. Annotated functions become DataFetchers
 * on the types the documents already named — they do not grow the schema.
 */
internal fun sdlGraphQLSchema(
    files: List<SchemaFile>,
    queries: List<Any>,
    mutations: List<Any>,
    subscriptions: List<Any>,
    typeInstances: List<Any>,
    json: Json,
): Pair<GraphQLSchema, List<RegisteredLoader>> {
    val registry = TypeDefinitionRegistry()
    files.forEach { file ->
        val parsed =
            try {
                SchemaParser().parse(file.source)
            } catch (failure: Exception) {
                throw GraphixException("cannot parse GraphQL schema '${file.path}': ${failure.message}", failure)
            }
        try {
            registry.merge(parsed)
        } catch (failure: Exception) {
            throw GraphixException("cannot merge GraphQL schema '${file.path}': ${failure.message}", failure)
        }
    }
    val typeFields = if (typeInstances.isEmpty()) emptyList() else collectTypeFields(typeInstances)
    val byType = linkedMapOf<String, TypeRuntimeWiring.Builder>()

    fun wire(
        parent: String,
        field: String,
        fetcher: DataFetcher<*>,
    ) {
        byType.getOrPut(parent) { TypeRuntimeWiring.newTypeWiring(parent) }.dataFetcher(field, fetcher)
    }
    rootFunctions(RootKind.QUERY, queries).forEach { (instance, function) ->
        wire("Query", function.graphQLName(RootKind.QUERY), resolverFetcher(instance, function, json))
    }
    rootFunctions(RootKind.MUTATION, mutations).forEach { (instance, function) ->
        wire("Mutation", function.graphQLName(RootKind.MUTATION), resolverFetcher(instance, function, json))
    }
    rootFunctions(RootKind.SUBSCRIPTION, subscriptions).forEach { (instance, function) ->
        wire(
            "Subscription",
            function.graphQLName(RootKind.SUBSCRIPTION),
            subscriptionFetcher(instance, function) { env -> bindArguments(function, env, json) },
        )
    }
    typeFields.forEach { field ->
        val fetcher =
            if (field.batched) {
                batchFieldFetcher(field.loaderName)
            } else {
                resolverFetcher(field.instance, field.function, json, field.parentParameter)
            }
        wire(field.parentName, field.fieldName, fetcher)
    }
    val wiring =
        RuntimeWiring
            .newRuntimeWiring()
            .scalar(Scalars.Long)
            .scalar(Scalars.Instant)
            .scalar(Scalars.Uuid)
    byType.values.forEach { wiring.type(it) }
    val schema =
        try {
            SchemaGenerator().makeExecutableSchema(registry, wiring.build())
        } catch (failure: Exception) {
            throw GraphixException("cannot build GraphQL schema from SDL: ${failure.message}", failure)
        }
    val declared = collectDeclaredLoaders(queries + mutations + subscriptions + typeInstances)
    val batched = typeFields.filter { it.batched }.map { it.toRegisteredLoader() }
    val names = mutableSetOf<String>()
    (declared + batched).forEach { loader ->
        if (!names.add(loader.name)) {
            throw GraphixException("duplicate DataLoader '${loader.name}'")
        }
    }
    return schema to declared + batched
}
