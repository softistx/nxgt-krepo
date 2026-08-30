package com.strange.graphix

import com.strange.common.serialization.lenientJson
import com.strange.graphix.execute.OperationScope
import com.strange.graphix.execute.toGraphixResult
import com.strange.graphix.schema.graphQLSchema
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass

/**
 * A GraphQL engine built from annotated Kotlin functions and `@Serializable` types.
 *
 * The application **names** its roots. There is no classpath scan here — Spring may collect
 * `@GraphQLController` beans; that is `stx-graphix-spring`. A data fetcher is not part of this
 * API: each `@Query` / `@Mutation` is a function on the instance passed to [GraphixBuilder.query]
 * or [GraphixBuilder.mutation], so a Spring `OrderService` lives on that instance's constructor,
 * not in [execute]'s context map.
 *
 * ```kotlin
 * val graphix = Graphix {
 *     query(ProductQueries(store))
 *     mutation(ProductMutations(store))
 * }
 * val result = graphix.execute(GraphixRequest("{ products { name } }"))
 * ```
 */
class Graphix internal constructor(
    private val engine: GraphQL,
) {
    /**
     * Runs one operation. Field failures land in [GraphixResult.errors]; this call still
     * returns. A document that cannot even be submitted throws [GraphixException]. A schema
     * that could not be built already threw from [Graphix], not from here.
     *
     * [context] is the per-operation bag, keyed by `KClass`. A resolver parameter annotated
     * `@GraphQLContext` is looked up there. A `CoroutineScope` is installed as well so `suspend`
     * resolvers run; it is cancelled when this returns. HTTP plugins do not yet put the call
     * or the security principal in [context] — whoever owns the HTTP request must, until they do.
     *
     * @param request the GraphQL document and already-decoded variables
     * @param context per-operation values for `@GraphQLContext` parameters, not Spring beans
     */
    suspend fun execute(
        request: GraphixRequest,
        context: Map<KClass<*>, Any> = emptyMap(),
    ): GraphixResult {
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val scope = CoroutineScope(currentCoroutineContext() + job + CoroutineName("graphql"))
        val input =
            ExecutionInput
                .newExecutionInput()
                .query(request.query)
                .variables(request.variables)
                .operationName(request.operationName)
                .graphQLContext { builder ->
                    builder.put(OperationScope, scope)
                    context.forEach { (key, value) -> builder.put(key, value) }
                }.build()
        return try {
            val result = engine.executeAsync(input).await()
            result.toGraphixResult()
        } finally {
            job.cancel()
        }
    }

    /** The schema as GraphQL SDL, for introspection dumps and tests. */
    fun sdl(): String = SchemaPrinter().print(engine.graphQLSchema)
}

/**
 * Accumulates query and mutation **instances**. Each instance is kept for the life of the
 * engine: the data fetcher calls methods on it, it does not construct a new one per request.
 */
class GraphixBuilder internal constructor(
    private val json: Json,
) {
    private val queries = mutableListOf<Any>()
    private val mutations = mutableListOf<Any>()

    /** Registers [instance]; every `@Query` function on it becomes a field on `Query`. */
    fun query(instance: Any) {
        queries += instance
    }

    /** Registers [instance]; every `@Mutation` function on it becomes a field on `Mutation`. */
    fun mutation(instance: Any) {
        mutations += instance
    }

    internal fun build(): Graphix {
        val schema = graphQLSchema(queries, mutations, json)
        return Graphix(GraphQL.newGraphQL(schema).build())
    }
}

/**
 * Builds a [Graphix] from named roots. [json] is how arguments and `@Serializable` types are
 * read; the default is `stx-common`'s lenient `Json`.
 */
fun Graphix(
    json: Json = lenientJson,
    block: GraphixBuilder.() -> Unit,
): Graphix = GraphixBuilder(json).apply(block).build()
