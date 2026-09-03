package com.softistx.graphix.koin

import com.softistx.graphix.GraphQLEngineCustomizer
import com.softistx.graphix.GraphixBuilder
import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.customize
import com.softistx.graphix.engine
import com.softistx.graphix.error.GraphixExceptionHandler
import com.softistx.graphix.error.exceptionHandler
import com.softistx.graphix.intercept.GraphixInterceptor
import com.softistx.graphix.intercept.intercept
import com.softistx.graphix.scalar.scalar
import com.softistx.graphix.schema.GraphixDirective
import com.softistx.graphix.schema.fieldDirective
import graphql.schema.GraphQLScalarType
import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/**
 * Builds the schema from what [koin] holds: every [GraphixResolver], [GraphQLScalarType],
 * [GraphixDirective], [GraphixCustomizer], [GraphixInterceptor], [GraphQLEngineCustomizer] and
 * [GraphixExceptionHandler] single, in one call.
 *
 * ```kotlin
 * install(GraphQL) {
 *     schema { fromKoin() }
 * }
 * ```
 *
 * This is a `GraphixBuilder` extension rather than a Ktor plugin flag, so it is the same call in a
 * Ktor application, in a Spring one and in a plain `Graphix { }`. Nothing here knows about a server.
 *
 * `Koin.getAll<T>()` enumerates natively, which is what makes this work at all: a container that can
 * only answer *"give me the T"* cannot answer *"give me every T"*, and collecting customizers is
 * entirely the second question.
 *
 * Adding to what it found is ordinary — `fromKoin()` then `resolvers(…)`, or the reverse — and
 * interceptors run in the order they were added either way.
 *
 * @param koin the container to read. Defaults to the global one, which is what `install(Koin)` sets.
 */
fun GraphixBuilder.fromKoin(koin: Koin = GlobalContext.get()) {
    koin.getAll<GraphixResolver>().takeIf { it.isNotEmpty() }?.let { resolvers(it) }
    koin.getAll<GraphQLScalarType>().forEach { scalar(it) }
    koin.getAll<GraphixDirective>().forEach { fieldDirective(it) }
    koin.getAll<GraphixCustomizer>().forEach { customize(it) }
    koin.getAll<GraphixInterceptor>().forEach { intercept(it) }
    koin.getAll<GraphQLEngineCustomizer>().forEach { engine(it) }
    koin.getAll<GraphixExceptionHandler>().forEach { exceptionHandler(it) }
}
