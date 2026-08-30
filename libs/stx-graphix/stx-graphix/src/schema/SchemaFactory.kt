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
import graphql.schema.GraphQLSchema
import kotlinx.serialization.json.Json

/** Builds the graphql-java schema from named roots and type fields. Needs at least one `@Query`. */
internal fun graphQLSchema(
    queries: List<Any>,
    mutations: List<Any>,
    subscriptions: List<Any>,
    typeInstances: List<Any>,
    loaderInstances: List<Any>,
    json: Json,
): Pair<GraphQLSchema, List<RegisteredLoader>> {
    if (queries.isEmpty()) throw GraphixException("Graphix needs at least one query root")
    val typeFields = collectTypeFields(typeInstances)
    val types = TypeMapper(json.serializersModule, typeFields.groupBy { it.parentName })
    val query =
        root("Query", RootKind.QUERY, queries, types) { instance, function ->
            resolverFetcher(instance, function, json)
        } ?: throw GraphixException("Graphix needs at least one query root")
    val mutation =
        if (mutations.isEmpty()) {
            null
        } else {
            root("Mutation", RootKind.MUTATION, mutations, types) { instance, function ->
                resolverFetcher(instance, function, json)
            }
        }
    val subscription =
        if (subscriptions.isEmpty()) {
            null
        } else {
            root("Subscription", RootKind.SUBSCRIPTION, subscriptions, types) { instance, function ->
                subscriptionFetcher(instance, function) { env -> bindArguments(function, env, json) }
            }
        }
    typeFields.forEach { types.output(it.parentType) }
    val registry =
        GraphQLCodeRegistry
            .newCodeRegistry()
            .putAll(query)
            .apply { if (mutation != null) putAll(mutation) }
            .apply { if (subscription != null) putAll(subscription) }
    typeFields.forEach { field ->
        val fetcher =
            if (field.batched) {
                batchFieldFetcher(field.loaderName)
            } else {
                resolverFetcher(field.instance, field.function, json, field.parentParameter)
            }
        registry.dataFetcher(FieldCoordinates.coordinates(field.parentName, field.fieldName), fetcher)
    }
    val schema =
        GraphQLSchema
            .newSchema()
            .query(query.type)
            .mutation(mutation?.type)
            .subscription(subscription?.type)
            .additionalTypes(types.additionalTypes())
            .additionalType(Scalars.Long)
            .additionalType(Scalars.Instant)
            .additionalType(Scalars.Uuid)
            .codeRegistry(registry.build())
            .build()
    val loaders =
        mergeLoaders(
            collectLoaders(loaderInstances + typeInstances + queries + mutations + subscriptions),
            typeFields,
        )
    validateLoads(queries + mutations + typeInstances, loaders.map { it.name }.toSet())
    return schema to loaders
}
