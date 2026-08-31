package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import com.strange.graphix.execute.RegisteredLoader
import com.strange.graphix.execute.batchFieldFetcher
import com.strange.graphix.execute.bindArguments
import com.strange.graphix.execute.resolverFetcher
import com.strange.graphix.execute.subscriptionFetcher
import com.strange.graphix.scalar.Scalars
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/** Builds the graphql-java schema from named roots and type fields. Needs at least one `@QueryMapping`. */
internal fun graphQLSchema(
    queries: List<Any>,
    mutations: List<Any>,
    subscriptions: List<Any>,
    typeInstances: List<Any>,
    json: Json,
    schemaFiles: List<SchemaFile> = emptyList(),
    customScalars: List<GraphQLScalarType> = emptyList(),
    kotlinScalars: Map<KClass<*>, GraphQLScalarType> = emptyMap(),
    fieldDirectives: Map<String, FieldDirectiveWrap> = emptyMap(),
    typeResolvers: Map<String, GraphixTypeName> = emptyMap(),
): Pair<GraphQLSchema, List<RegisteredLoader>> {
    if (queries.isEmpty()) throw GraphixException("Graphix needs at least one query root")
    if (schemaFiles.isNotEmpty()) {
        return schemaFiles.sdlSchema(
            queries,
            mutations,
            subscriptions,
            typeInstances,
            json,
            customScalars,
            fieldDirectives,
            typeResolvers,
        )
    }
    val typeFields = collectTypeFields(typeInstances)
    val types = TypeMapper(json.serializersModule, typeFields.groupBy { it.parentName }, kotlinScalars)
    val query =
        root("Query", RootKind.QUERY, queries, types) { instance, function ->
            resolverFetcher(instance, function, json).withDirectives(function, fieldDirectives)
        } ?: throw GraphixException("Graphix needs at least one query root")
    val mutation =
        if (mutations.isEmpty()) {
            null
        } else {
            root("Mutation", RootKind.MUTATION, mutations, types) { instance, function ->
                resolverFetcher(instance, function, json).withDirectives(function, fieldDirectives)
            }
        }
    val subscription =
        if (subscriptions.isEmpty()) {
            null
        } else {
            root("Subscription", RootKind.SUBSCRIPTION, subscriptions, types) { instance, function ->
                subscriptionFetcher(instance, function) { env -> bindArguments(function, env, json) }
                    .withDirectives(function, fieldDirectives)
            }
        }
    typeFields.forEach { types.output(it.parentType) }
    val registry =
        GraphQLCodeRegistry
            .newCodeRegistry()
            .putAll(query)
            .apply { if (mutation != null) putAll(mutation) }
            .apply { if (subscription != null) putAll(subscription) }
    val resolver = GraphixTypeResolver(typeResolvers)
    types.abstractTypes().forEach { abstract ->
        when (abstract) {
            is GraphQLInterfaceType -> registry.typeResolver(abstract, resolver)
            is GraphQLUnionType -> registry.typeResolver(abstract, resolver)
            else -> Unit
        }
    }
    typeFields.forEach { field ->
        val fetcher =
            if (field.batched) {
                batchFieldFetcher(field)
            } else {
                resolverFetcher(field.instance, field.function, json, field.parentParameter)
                    .withDirectives(field.function, fieldDirectives)
            }
        // A data fetcher is looked up by the concrete object type, never inherited down an
        // interface, so a mapping on an interface has to be registered on every implementor.
        val targets = types.implementorsOf(field.parentName).ifEmpty { listOf(field.parentName) }
        targets.forEach { target ->
            registry.dataFetcherIfAbsent(FieldCoordinates.coordinates(target, field.fieldName), fetcher)
        }
    }
    val schema =
        try {
            GraphQLSchema
                .newSchema()
                .query(query.type)
                .mutation(mutation?.type)
                .subscription(subscription?.type)
                .additionalTypes(types.additionalTypes())
                .additionalType(Scalars.Long)
                .additionalType(Scalars.Instant)
                .additionalType(Scalars.Uuid)
                .apply { customScalars.forEach { additionalType(it) } }
                .codeRegistry(registry.build())
                .build()
        } catch (failure: Exception) {
            throw GraphixException("cannot build GraphQL schema: ${failure.message}", failure)
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
