package com.strange.graphix.execute

import com.strange.graphix.schema.TypeFieldMeta
import com.strange.graphix.schema.graphQLName
import graphql.schema.DataFetcher

/**
 * `@BatchMapping` field. `load` must return immediately — wrapping it in `future { }` is the pattern
 * graphql-java warns never completes. The DataLoader key is the parent plus this field's
 * `@Argument` values so aliases with different arguments do not share a cached row.
 */
internal fun batchFieldFetcher(field: TypeFieldMeta): DataFetcher<*> =
    DataFetcher { environment ->
        val loader =
            environment.getDataLoader<Any, Any>(field.loaderName)
                ?: error("no DataLoader '${field.loaderName}' — Graphix.execute must register @BatchMapping loaders")
        val parent: Any =
            environment.getSource()
                ?: error("no parent source for '${field.loaderName}'")
        val raw = environment.arguments
        val arguments =
            field.argumentParameters.associate { parameter ->
                val name = parameter.graphQLName()
                name to raw[name]
            }
        loader.load(BatchKey(parent, arguments), environment)
    }
