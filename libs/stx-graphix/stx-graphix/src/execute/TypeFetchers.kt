package com.strange.graphix.execute

import com.strange.graphix.schema.TypeFieldMeta
import graphql.schema.DataFetcher
import kotlinx.serialization.json.Json

/**
 * Per-parent `@Field`. The parent is `env.source`, not a GraphQL argument.
 */
internal fun typeFieldFetcher(
    field: TypeFieldMeta,
    json: Json,
): DataFetcher<*> =
    suspendFetcher(field.instance, field.function) { env ->
        val bound = bindArguments(field.function, env, json, skip = setOf(field.parentParameter)).toMutableMap()
        bound[field.parentParameter] = env.getSource()
        bound
    }

/**
 * `@Batch` field. `load` must return immediately — wrapping it in `future { }` is the pattern
 * graphql-java warns never completes.
 */
internal fun batchFieldFetcher(loaderName: String): DataFetcher<*> =
    DataFetcher { environment ->
        val loader =
            environment.getDataLoader<Any, Any>(loaderName)
                ?: error("no DataLoader '$loaderName' — Graphix.execute must register @Batch loaders")
        val parent =
            environment.getSource<Any>()
                ?: error("no parent source for '$loaderName'")
        loader.load(parent)
    }
