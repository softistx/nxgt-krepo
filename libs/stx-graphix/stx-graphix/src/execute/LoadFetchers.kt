package com.strange.graphix.execute

import com.strange.graphix.GraphixException
import com.strange.graphix.schema.Load
import com.strange.graphix.schema.graphQLName
import com.strange.graphix.schema.isGraphQLContext
import com.strange.graphix.schema.isLoad
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.serialization.json.Json
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters

/**
 * A resolver with `@Load`. `dataLoader.load(key)` returns immediately; the Kotlin function
 * runs after the batch, in `thenCompose`.
 */
internal fun loadFetcher(
    instance: Any,
    function: KFunction<*>,
    json: Json,
    parent: KParameter? = null,
): DataFetcher<*> {
    val loadParameters = function.valueParameters.filter { it.isLoad() }
    if (loadParameters.size != 1) {
        throw GraphixException("@Load on ${function.name} needs exactly one @Load parameter")
    }
    val loadParameter = loadParameters.single()
    val annotation = loadParameter.findAnnotation<Load>()!!
    val loaderName = annotation.name.ifEmpty { loadParameter.name ?: error("@Load needs a parameter name") }
    return DataFetcher { environment ->
        val loader =
            environment.getDataLoader<Any, Any>(loaderName)
                ?: error("no DataLoader '$loaderName'")
        val key = loadKey(environment, function, annotation, parent)
        val scope =
            environment.graphQlContext.get<CoroutineScope>(OperationScope)
                ?: error("no CoroutineScope in GraphQLContext")
        loader.load(key).thenCompose { loaded ->
            scope.future {
                val arguments = LinkedHashMap<KParameter, Any?>()
                val instanceParameter =
                    function.instanceParameter
                        ?: error("${function.name} is not a member function")
                arguments[instanceParameter] = instance
                if (parent != null) arguments[parent] = environment.getSource()
                arguments.putAll(bindArguments(function, environment, json, skip = setOfNotNull(parent)))
                arguments[loadParameter] = loaded
                if (function.isSuspend) {
                    function.callSuspendBy(arguments)
                } else {
                    function.callBy(arguments)
                }
            }
        }
    }
}

internal fun resolverFetcher(
    instance: Any,
    function: KFunction<*>,
    json: Json,
    parent: KParameter? = null,
): DataFetcher<*> =
    if (function.valueParameters.any { it.isLoad() }) {
        loadFetcher(instance, function, json, parent)
    } else {
        suspendFetcher(instance, function) { env ->
            val bound = bindArguments(function, env, json, skip = setOfNotNull(parent)).toMutableMap()
            if (parent != null) bound[parent] = env.getSource()
            bound
        }
    }

private fun loadKey(
    environment: DataFetchingEnvironment,
    function: KFunction<*>,
    load: Load,
    parent: KParameter?,
): Any {
    val from = load.from
    if (parent != null) {
        val source =
            environment.getSource<Any>()
                ?: throw GraphixException("@Load on ${function.name} has no parent")
        if (from.isEmpty()) return source
        val property =
            source::class.memberProperties.find { it.name == from }
                ?: throw GraphixException("@Load from '$from' is not a property of ${source::class.simpleName}")
        return property.getter.call(source)
            ?: throw GraphixException("@Load from '$from' was null on ${source::class.simpleName}")
    }
    val argumentName =
        from.ifEmpty {
            function.valueParameters
                .firstOrNull { !it.isGraphQLContext() && !it.isLoad() }
                ?.graphQLName()
                ?: throw GraphixException("@Load on ${function.name} needs a GraphQL argument for the key")
        }
    return environment.getArgument(argumentName)
        ?: throw GraphixException("@Load key argument '$argumentName' was missing")
}
