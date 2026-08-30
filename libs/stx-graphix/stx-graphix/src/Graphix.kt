package com.strange.graphix

import com.strange.common.serialization.lenientJson
import com.strange.graphix.execute.executionInput
import com.strange.graphix.execute.toGraphixResult
import com.strange.graphix.schema.graphQLSchema
import graphql.GraphQL
import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.reactivestreams.Publisher
import kotlin.reflect.KClass

/**
 * A GraphQL engine built from annotated Kotlin functions and `@Serializable` types.
 *
 * The application **names** its roots. There is no classpath scan here — Spring may collect
 * `@GraphQLController` beans; that is `stx-graphix-spring`. A data fetcher is not part of this
 * API: each `@Query` / `@Mutation` / `@Subscription` is a function on the instance passed to
 * [GraphixBuilder.query], [GraphixBuilder.mutation] or [GraphixBuilder.subscription], so a
 * Spring `OrderService` lives on that instance's constructor, not in [execute]'s context map.
 *
 * ```kotlin
 * val graphix = Graphix {
 *     query(ProductQueries(store))
 *     mutation(ProductMutations(store))
 *     subscription(ProductSubscriptions(store))
 * }
 * val result = graphix.execute(GraphixRequest("{ products { name } }"))
 * graphix.subscribe(GraphixRequest("subscription { productAdded { name } }"))
 * ```
 */
class Graphix internal constructor(
    internal val engine: GraphQL,
) {
    /**
     * Runs one query or mutation. Field failures land in [GraphixResult.errors]; this call
     * still returns. A document that cannot even be submitted throws [GraphixException]. A
     * schema that could not be built already threw from [Graphix], not from here.
     *
     * A **subscription** is [subscribe] — graphql-java's result is a `Publisher`, which this
     * method refuses rather than serialising as a single JSON object.
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
        return try {
            val result = engine.executeAsync(executionInput(request, context, scope)).await()
            if (result.getData<Any?>() is Publisher<*>) {
                throw GraphixException("this is a subscription — use Graphix.subscribe")
            }
            result.toGraphixResult()
        } finally {
            job.cancel()
        }
    }

    /** The schema as GraphQL SDL, for introspection dumps and tests. */
    fun sdl(): String = SchemaPrinter().print(engine.graphQLSchema)
}

/**
 * Accumulates query, mutation and subscription **instances**. Each instance is kept for the
 * life of the engine: the data fetcher calls methods on it, it does not construct a new one
 * per request.
 */
class GraphixBuilder internal constructor(
    private val json: Json,
) {
    private val queries = mutableListOf<Any>()
    private val mutations = mutableListOf<Any>()
    private val subscriptions = mutableListOf<Any>()

    /** Registers [instance]; every `@Query` function on it becomes a field on `Query`. */
    fun query(instance: Any) {
        queries += instance
    }

    /** Registers [instance]; every `@Mutation` function on it becomes a field on `Mutation`. */
    fun mutation(instance: Any) {
        mutations += instance
    }

    /** Registers [instance]; every `@Subscription` function on it becomes a field on `Subscription`. */
    fun subscription(instance: Any) {
        subscriptions += instance
    }

    internal fun build(): Graphix {
        val schema = graphQLSchema(queries, mutations, subscriptions, json)
        return Graphix(GraphQL.newGraphQL(schema).build())
    }
}

/**
 * Builds a [Graphix] from named roots. [json] is how arguments and `@Serializable` types are
 * read; the default is `stx-common`'s lenient `Json`.
 *
 * [GraphixBuilder.subscription] is optional. GraphQL still requires a query root.
 */
fun Graphix(
    json: Json = lenientJson,
    block: GraphixBuilder.() -> Unit,
): Graphix = GraphixBuilder(json).apply(block).build()
