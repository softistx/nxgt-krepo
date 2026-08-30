package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import com.strange.graphix.execute.BatchBinding
import com.strange.graphix.execute.batchFieldFetcher
import com.strange.graphix.execute.bindArguments
import com.strange.graphix.execute.subscriptionFetcher
import com.strange.graphix.execute.suspendFetcher
import com.strange.graphix.execute.typeFieldFetcher
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
    json: Json,
): Pair<GraphQLSchema, List<BatchBinding>> {
    if (queries.isEmpty()) throw GraphixException("Graphix needs at least one query root")
    val typeFields = collectTypeFields(typeInstances)
    val types = TypeMapper(json.serializersModule, typeFields.groupBy { it.parentName })
    val query =
        root("Query", RootKind.QUERY, queries, types) { instance, function ->
            suspendFetcher(instance, function) { env -> bindArguments(function, env, json) }
        } ?: throw GraphixException("Graphix needs at least one query root")
    val mutation =
        if (mutations.isEmpty()) {
            null
        } else {
            root("Mutation", RootKind.MUTATION, mutations, types) { instance, function ->
                suspendFetcher(instance, function) { env -> bindArguments(function, env, json) }
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
                typeFieldFetcher(field, json)
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
    return schema to typeFields.filter { it.batched }.map { BatchBinding(it) }
}
