package com.strange.graphix.execute

import graphql.schema.DataFetcher

/**
 * `@BatchMapping` field. `load` must return immediately — wrapping it in `future { }` is the pattern
 * graphql-java warns never completes.
 */
internal fun batchFieldFetcher(loaderName: String): DataFetcher<*> =
    DataFetcher { environment ->
        val loader =
            environment.getDataLoader<Any, Any>(loaderName)
                ?: error("no DataLoader '$loaderName' — Graphix.execute must register @BatchMapping loaders")
        val parent: Any =
            environment.getSource()
                ?: error("no parent source for '$loaderName'")
        loader.load(parent)
    }
