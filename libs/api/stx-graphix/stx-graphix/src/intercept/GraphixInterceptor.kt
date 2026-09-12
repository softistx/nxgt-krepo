package com.softistx.graphix.intercept

import com.softistx.graphix.GraphixBuilder
import com.softistx.graphix.GraphixResult
import kotlinx.coroutines.flow.Flow

/**
 * Wraps one GraphQL operation: rewrite the request, contribute to the operation context,
 * short-circuit, or shape what comes back.
 *
 * ```kotlin
 * Graphix {
 *     resolvers(ProductQueries(store))
 *     intercept {
 *         put(Caller(token(request)))
 *         proceed()
 *     }
 * }
 * ```
 *
 * **The result is a `Flow` for every operation kind**, of one element for a query or a mutation and
 * of many for a subscription. That is what lets one interceptor read the same over HTTP, over SSE
 * and over graphql-ws — `proceed().map { … }` shapes a single response and every subscription event
 * with the same line, and there is no second hook to write for the streaming case.
 *
 * Interceptors run in registration order, outermost first, and each one decides whether to call
 * [GraphixChain.proceed]. One that does not is a request the engine never sees.
 *
 * This is the operation-level twin of a field directive: `GraphixDirective` wraps one field with
 * the same `proceed()` shape, and sees the context this put there.
 */
fun interface GraphixInterceptor {
    suspend fun GraphixChain.intercept(): Flow<GraphixResult>
}

/**
 * Registers [interceptor]. Spring collects every bean of this type in declaration order, Ktor's
 * plugin takes `intercept { }` blocks, and `stx-graphix-koin`'s `fromKoin()` takes every single.
 */
fun GraphixBuilder.intercept(interceptor: GraphixInterceptor) {
    addInterceptor(interceptor)
}

/** [intercept], written as a block. */
fun GraphixBuilder.intercept(block: suspend GraphixChain.() -> Flow<GraphixResult>) {
    addInterceptor(GraphixInterceptor { block() })
}
