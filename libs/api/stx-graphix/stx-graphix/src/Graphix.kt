package com.softistx.graphix

import com.softistx.common.serialization.lenientJson
import com.softistx.graphix.error.ErrorHandlers
import com.softistx.graphix.error.handleOutsideField
import com.softistx.graphix.execute.RegisteredLoader
import com.softistx.graphix.execute.executionInput
import com.softistx.graphix.execute.toGraphixResult
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.runChain
import com.softistx.graphix.message.GraphixMessages
import com.softistx.graphix.validation.GraphixValidation
import graphql.GraphQL
import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.reactivestreams.Publisher
import kotlin.reflect.KClass

/**
 * A GraphQL engine built from annotated Kotlin functions and `@Serializable` types.
 *
 * The application **names** its roots. There is no classpath scan for resolver classes — Spring
 * may collect `@GraphQLController` beans; that is `stx-graphix-spring`. Schema documents under
 * `classpath:graphql/` are scanned, Spring GraphQL's default. A data fetcher is not part of this
 * API: each `@QueryMapping` / `@MutationMapping` / `@SubscriptionMapping` / `@SchemaMapping` is a
 * function on an instance passed to [GraphixBuilder.resolvers], so a Spring `OrderService` lives on
 * that instance's constructor, not in [execute]'s context map.
 *
 * ```kotlin
 * val graphix = Graphix {
 *     resolvers(
 *         ProductQueries(store),
 *         ProductMutations(store),
 *         ProductSubscriptions(store),
 *         ProductFields(reviews),
 *     )
 * }
 * val result = graphix.execute(GraphixRequest("{ products { name } }"))
 * graphix.subscribe(GraphixRequest("subscription { productAdded { name } }"))
 * ```
 */
class Graphix internal constructor(
    internal val engine: GraphQL,
    internal val loaders: List<RegisteredLoader> = emptyList(),
    internal val validation: GraphixValidation? = null,
    internal val introspection: Boolean = true,
    internal val messages: GraphixMessages = GraphixMessages.Bundled,
    internal val interceptors: List<GraphixInterceptor> = emptyList(),
    /** Kept past `build()` because the seats outside graphql-java need it too. */
    internal val errorHandlers: ErrorHandlers = ErrorHandlers(emptyList(), null),
    internal val maxListElements: Int? = null,
) {
    /**
     * Runs one query or mutation. Field failures land in [GraphixResult.errors]; this call
     * still returns. A document that cannot even be submitted throws [GraphixException]. A
     * schema that could not be built already threw from [Graphix], not from here.
     *
     * A **subscription** is [subscribe] — graphql-java's result is a `Publisher`, which this
     * method refuses rather than serialising as a single JSON object.
     *
     * [context] is the per-operation bag, keyed by `KClass`, and it is what a resolver's
     * framework parameters are read from. A `CoroutineScope`
     * is installed alongside it so `suspend` resolvers run; it is cancelled when this returns.
     * An HTTP integration puts its call there — `ApplicationCall`, `ServerWebExchange` — and
     * [GraphixInterceptor] is how an application adds to it without owning the call site.
     *
     * @param request the GraphQL document and already-decoded variables
     * @param context per-operation values, not Spring beans
     */
    suspend fun execute(
        request: GraphixRequest,
        context: Map<KClass<*>, Any> = emptyMap(),
    ): GraphixResult =
        try {
            runChain(interceptors, request, context) { operation, values ->
                flow { emit(executeOnce(operation, values)) }
            }.single()
        } catch (failure: Throwable) {
            // An interceptor throw never reached graphql-java, so it never reached the seat there.
            // Unclaimed, it is rethrown and nothing changes.
            handleOutsideField(failure, request, context) ?: throw failure
        }

    private suspend fun executeOnce(
        request: GraphixRequest,
        context: Map<KClass<*>, Any>,
    ): GraphixResult {
        val job = SupervisorJob(currentCoroutineContext()[Job])
        val scope = CoroutineScope(currentCoroutineContext() + job + CoroutineName("graphql"))
        return try {
            val result =
                engine
                    .executeAsync(executionInput(request, context, scope, loaders, validation, introspection, messages, maxListElements))
                    .await()
            if (result.getData<Any>() is Publisher<*>) {
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
 * Builds a [Graphix] from named roots. [json] is how arguments and `@Serializable` types are
 * read; the default is `stx-common`'s lenient `Json`.
 *
 * [GraphixBuilder.resolvers] takes every root and type object in one call. GraphQL still
 * requires a query root.
 */
fun Graphix(
    json: Json = lenientJson,
    block: GraphixBuilder.() -> Unit,
): Graphix = GraphixBuilder(json).apply(block).build()
