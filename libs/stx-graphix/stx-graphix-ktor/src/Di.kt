package com.softistx.graphix.ktor

import com.softistx.graphix.GraphQLEngineCustomizer
import com.softistx.graphix.GraphixBuilder
import com.softistx.graphix.GraphixCustomizer
import com.softistx.graphix.customize
import com.softistx.graphix.engine
import com.softistx.graphix.scalar.scalar
import com.softistx.graphix.schema.GraphixDirective
import com.softistx.graphix.schema.fieldDirective
import graphql.schema.GraphQLScalarType
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.MissingDependencyException
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking

/**
 * Collects the same types Spring collects as beans: [GraphixCustomizer], [GraphQLScalarType],
 * [GraphixDirective], [GraphQLEngineCustomizer] — singles or lists.
 */
internal fun Application.applyGraphixDi(builder: GraphixBuilder) {
    diGet<GraphixCustomizer>()?.let { builder.customize(it) }
    diGet<List<GraphixCustomizer>>()?.forEach { builder.customize(it) }
    diGet<GraphQLScalarType>()?.let { builder.scalar(it) }
    diGet<List<GraphQLScalarType>>()?.forEach { builder.scalar(it) }
    diGet<GraphixDirective>()?.let { builder.fieldDirective(it) }
    diGet<List<GraphixDirective>>()?.forEach { builder.fieldDirective(it) }
    diGet<GraphQLEngineCustomizer>()?.let { builder.engine(it) }
    diGet<List<GraphQLEngineCustomizer>>()?.forEach { builder.engine(it) }
}

private inline fun <reified T : Any> Application.diGet(): T? =
    try {
        dependencies.getBlocking(DependencyKey<T>())
    } catch (_: MissingDependencyException) {
        null
    }
