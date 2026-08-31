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
internal fun List<SchemaFile>.sdlSchema(
    queries: List<Any>,
    mutations: List<Any>,
    subscriptions: List<Any>,
    typeInstances: List<Any>,
    json: Json,
    customScalars: List<graphql.schema.GraphQLScalarType> = emptyList(),
    fieldDirectives: Map<String, FieldDirectiveWrap> = emptyMap(),
    typeResolvers: Map<String, GraphixTypeName> = emptyMap(),
): Pair<GraphQLSchema, List<RegisteredLoader>> {
    val registry = typeRegistry()
    val typeFields = if (typeInstances.isEmpty()) emptyList() else collectTypeFields(typeInstances)
    val byType = linkedMapOf<String, TypeRuntimeWiring.Builder>()
    // RuntimeWiring is strict: a second fetcher for one coordinate throws rather than replacing.
    val wired = mutableSetOf<Pair<String, String>>()

    fun wire(
        parent: String,
        field: String,
        fetcher: DataFetcher<*>,
    ) {
        if (!wired.add(parent to field)) return
        byType.getOrPut(parent) { TypeRuntimeWiring.newTypeWiring(parent) }.dataFetcher(field, fetcher)
    }
    queries.rootFunctions(RootKind.QUERY).forEach { (instance, function) ->
        wire(
            "Query",
            function.graphQLName(RootKind.QUERY),
            resolverFetcher(instance, function, json).withDirectives(function, fieldDirectives),
        )
    }
    mutations.rootFunctions(RootKind.MUTATION).forEach { (instance, function) ->
        wire(
            "Mutation",
            function.graphQLName(RootKind.MUTATION),
            resolverFetcher(instance, function, json).withDirectives(function, fieldDirectives),
        )
    }
    subscriptions.rootFunctions(RootKind.SUBSCRIPTION).forEach { (instance, function) ->
        wire(
            "Subscription",
            function.graphQLName(RootKind.SUBSCRIPTION),
            subscriptionFetcher(instance, function) { env -> bindArguments(function, env, json) }
                .withDirectives(function, fieldDirectives),
        )
    }
    // Concrete parents first: an explicit mapping on an implementor outranks the one it inherits.
    val (abstractParents, concreteParents) =
        typeFields.partition { registry.implementorsOf(it.parentName).isNotEmpty() }
    (concreteParents + abstractParents).forEach { field ->
        val fetcher =
            if (field.batched) {
                batchFieldFetcher(field)
            } else {
                resolverFetcher(field.instance, field.function, json, field.parentParameter)
                    .withDirectives(field.function, fieldDirectives)
            }
        val implementors = registry.implementorsOf(field.parentName)
        if (implementors.isEmpty()) {
            wire(field.parentName, field.fieldName, fetcher)
            return@forEach
        }
        if (registry.isUnion(field.parentName)) {
            throw GraphixException(
                "@SchemaMapping ${field.function.name} targets union '${field.parentName}' — " +
                    "a GraphQL union has no fields; put the field on each member type, or make it an interface",
            )
        }
        // A data fetcher is never inherited down an interface: register it on every implementor.
        wire(field.parentName, field.fieldName, fetcher)
        implementors.forEach { wire(it, field.fieldName, fetcher) }
    }
    val wiring =
        RuntimeWiring
            .newRuntimeWiring()
            .scalar(Scalars.Long)
            .scalar(Scalars.Instant)
            .scalar(Scalars.Uuid)
    customScalars.forEach { wiring.scalar(it) }
    fieldDirectives.forEach { (name, wrap) ->
        wiring.directive(name, GraphixDirective(name, wrap).toSchemaWiring())
    }
    // Without a type resolver, SchemaGenerator refuses every interface and union in the document.
    val resolver = GraphixTypeResolver(typeResolvers)
    registry.abstractTypeNames().forEach { name ->
        byType.getOrPut(name) { TypeRuntimeWiring.newTypeWiring(name) }.typeResolver(resolver)
    }
    byType.values.forEach { wiring.type(it) }
    val schema =
        try {
            SchemaGenerator().makeExecutableSchema(registry, wiring.build())
        } catch (failure: Exception) {
            throw GraphixException("cannot build GraphQL schema from SDL: ${failure.message}", failure)
        }
    val declared = collectDeclaredLoaders(queries + mutations + subscriptions + typeInstances)
    val batched = typeFields.filter { it.batched }.map { it.toRegisteredLoader(json) }
    val names = mutableSetOf<String>()
    (declared + batched).forEach { loader ->
        if (!names.add(loader.name)) {
            throw GraphixException("duplicate DataLoader '${loader.name}'")
        }
    }
    return schema to declared + batched
}

private fun List<SchemaFile>.typeRegistry(): TypeDefinitionRegistry {
    val registry = TypeDefinitionRegistry()
    forEach { file ->
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
    return registry
}
