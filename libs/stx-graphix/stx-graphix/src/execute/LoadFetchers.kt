package com.strange.graphix.execute

import graphql.schema.DataFetcher
import kotlinx.serialization.json.Json
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

internal fun resolverFetcher(
    instance: Any,
    function: KFunction<*>,
    json: Json,
    parent: KParameter? = null,
): DataFetcher<*> =
    suspendFetcher(instance, function) { env ->
        val bound = bindArguments(function, env, json, skip = setOfNotNull(parent)).toMutableMap()
        if (parent != null) bound[parent] = env.getSource()
        bound
    }
