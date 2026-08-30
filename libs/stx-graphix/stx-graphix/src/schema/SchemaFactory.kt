package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import com.strange.graphix.execute.bindArguments
import com.strange.graphix.execute.suspendFetcher
import com.strange.graphix.scalar.Scalars
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLSchema
import kotlinx.serialization.json.Json

/** Builds the graphql-java schema from named query/mutation instances. Needs at least one `@Query`. */
internal fun graphQLSchema(
    queries: List<Any>,
    mutations: List<Any>,
    json: Json,
): GraphQLSchema {
    if (queries.isEmpty()) throw GraphixException("Graphix needs at least one query root")
    val types = TypeMapper(json.serializersModule)
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
    val registry =
        GraphQLCodeRegistry
            .newCodeRegistry()
            .putAll(query)
            .apply { if (mutation != null) putAll(mutation) }
            .build()
    return GraphQLSchema
        .newSchema()
        .query(query.type)
        .mutation(mutation?.type)
        .additionalTypes(types.additionalTypes())
        .additionalType(Scalars.Long)
        .additionalType(Scalars.Instant)
        .additionalType(Scalars.Uuid)
        .codeRegistry(registry)
        .build()
}
