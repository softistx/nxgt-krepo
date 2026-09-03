package com.softistx.graphix.intercept

import com.softistx.graphix.GraphixRequest
import com.softistx.graphix.GraphixResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KClass

/**
 * One [GraphixInterceptor]'s view of the operation: the request on its way in, the context being
 * assembled for it, and [proceed] — the rest of the chain, ending at the engine.
 *
 * The context is one bag shared by the whole chain, so an interceptor sees what the ones before it
 * put there. It is keyed by `KClass` and lands in graphql-java's `GraphQLContext`, which is where a
 * resolver's `@GraphQLContext` parameter and its framework parameters are read from.
 */
class GraphixChain internal constructor(
    /** The operation. Assigning a new one is how an interceptor rewrites the document or variables. */
    var request: GraphixRequest,
    private val context: MutableMap<KClass<*>, Any>,
    private val remaining: List<GraphixInterceptor>,
    private val terminal: suspend (GraphixRequest, Map<KClass<*>, Any>) -> Flow<GraphixResult>,
) {
    /** Puts [value] in the operation context under [key]. */
    fun <T : Any> put(
        key: KClass<T>,
        value: T,
    ) {
        context[key] = value
    }

    /** What is in the operation context under [key], from an earlier interceptor or the caller. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: KClass<T>): T? = context[key] as T?

    /**
     * The rest of the chain, with whatever this interceptor has done to [request] and the context.
     *
     * Not calling it is a short-circuit: the engine never sees the operation, and whatever flow is
     * returned instead is the answer. Calling it twice re-runs everything downstream, which is a
     * retry and is left available on purpose.
     */
    suspend fun proceed(): Flow<GraphixResult> {
        val next = remaining.firstOrNull() ?: return terminal(request, context)
        val chain = GraphixChain(request, context, remaining.drop(1), terminal)
        return with(next) { chain.intercept() }
    }
}

/** [GraphixChain.put], with the key read off the type. */
inline fun <reified T : Any> GraphixChain.put(value: T) = put(T::class, value)

/** [GraphixChain.get], with the key read off the type. */
inline fun <reified T : Any> GraphixChain.get(): T? = get(T::class)

/**
 * Runs [interceptors] outermost-first around [terminal].
 *
 * Cold: nothing runs until the returned flow is collected, so `subscribe` can hand one back without
 * suspending. [context] is copied, so the caller's map is not the one the chain mutates.
 */
internal fun runChain(
    interceptors: List<GraphixInterceptor>,
    request: GraphixRequest,
    context: Map<KClass<*>, Any>,
    terminal: suspend (GraphixRequest, Map<KClass<*>, Any>) -> Flow<GraphixResult>,
): Flow<GraphixResult> =
    flow {
        val chain = GraphixChain(request, context.toMutableMap(), interceptors, terminal)
        emitAll(chain.proceed())
    }
