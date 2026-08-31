package com.strange.graphix.ktor

import com.strange.graphix.GraphQLEngineCustomizer
import com.strange.graphix.GraphixBuilder
import com.strange.graphix.GraphixCustomizer
import com.strange.graphix.customize
import com.strange.graphix.engine
import com.strange.graphix.scalar.scalar
import com.strange.graphix.schema.GraphixDirective
import com.strange.graphix.schema.fieldDirective
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
