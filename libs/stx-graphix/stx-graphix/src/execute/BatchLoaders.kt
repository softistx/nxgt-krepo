package com.softistx.graphix.execute

import com.softistx.graphix.GraphixException
import com.softistx.graphix.schema.TypeFieldMeta
import com.softistx.graphix.schema.graphQLName
import com.softistx.graphix.schema.isArgument
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.serialization.json.Json
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters
import graphql.GraphQLContext as OperationContext

internal data class RegisteredLoader(
    val name: String,
    val loadBatch: suspend (
        keys: Set<Any>,
        context: Map<KClass<*>, Any>,
        environment: DataFetchingEnvironment?,
    ) -> Map<Any, Any>,
)

internal fun dataLoaderRegistry(
    loaders: List<RegisteredLoader>,
    scope: CoroutineScope,
    context: Map<KClass<*>, Any>,
): DataLoaderRegistry {
    val registry = DataLoaderRegistry()
    loaders.forEach { loader ->
        registry.register(
            loader.name,
            DataLoaderFactory.newMappedDataLoader<Any, Any> { keys, batchEnv ->
                scope.future { loader.loadBatch(keys, context, batchEnv.representativeDfe(keys)) }
            },
        )
    }
    return registry
}

/**
 * One framework parameter of a `@BatchMapping`.
 *
 * A batch has no field of its own, so its `DataFetchingEnvironment` is the representative one taken
 * from the keys — and there may be none. With it, this is exactly [contextValue]. Without it, the
 * two graphql-java types cannot be produced at all and say so, while an application type still comes
 * out of the operation context the loader was built with.
 */
private fun batchContextValue(
    parameter: KParameter,
    environment: DataFetchingEnvironment?,
    context: Map<KClass<*>, Any>,
    functionName: String,
): Any {
    if (environment != null) return contextValue(parameter, environment)
    val classifier =
        parameter.type.classifier as? KClass<*>
            ?: throw GraphixException("${parameter.name} needs a class type")
    if (classifier.isSubclassOf(DataFetchingEnvironment::class) || classifier.isSubclassOf(OperationContext::class)) {
        throw GraphixException("@BatchMapping $functionName needs a DataFetchingEnvironment")
    }
    return context[classifier]
        ?: throw GraphixException("no ${classifier.qualifiedName} in the operation context")
}

private fun BatchLoaderEnvironment.representativeDfe(keys: Set<Any>): DataFetchingEnvironment? {
    keys.forEach { key ->
        (keyContexts[key] as? DataFetchingEnvironment)?.let { return it }
    }
    return keyContexts.values.filterIsInstance<DataFetchingEnvironment>().firstOrNull()
}

/** Parent plus this field's `@Argument` values. One DataLoader per operation, keyed so aliases do not collide. */
internal data class BatchKey(
    val parent: Any,
    val arguments: Map<String, Any?>,
)

internal suspend fun loadBatchMapping(
    field: TypeFieldMeta,
    keys: Set<Any>,
    context: Map<KClass<*>, Any>,
    environment: DataFetchingEnvironment?,
    json: Json,
): Map<Any, Any> {
    val batchKeys = keys.map { it as? BatchKey ?: BatchKey(it, emptyMap()) }
    return buildMap {
        batchKeys.groupBy { it.arguments }.forEach { (argumentValues, group) ->
            val parents = group.map { it.parent }
            val byParent = invokeBatchMapping(field, parents, argumentValues, context, environment, json)
            group.forEach { key ->
                byParent[key.parent]?.let { put(key, it) }
            }
        }
    }
}

private suspend fun invokeBatchMapping(
    field: TypeFieldMeta,
    parents: List<Any>,
    argumentValues: Map<String, Any?>,
    context: Map<KClass<*>, Any>,
    environment: DataFetchingEnvironment?,
    json: Json,
): Map<Any, Any> {
    val function = field.function
    val arguments = LinkedHashMap<kotlin.reflect.KParameter, Any?>()
    val instanceParameter =
        function.instanceParameter
            ?: error("${function.name} is not a member function")
    arguments[instanceParameter] = field.instance
    arguments[field.parentParameter] = parents
    function.valueParameters.forEach { parameter ->
        if (parameter == field.parentParameter) return@forEach
        if (parameter.isArgument()) {
            val raw = argumentValues[parameter.graphQLName()]
            if (raw == null && parameter.isOptional) return@forEach
            arguments[parameter] = decode(raw, parameter, json)
            return@forEach
        }
        // The same rule as `bindArguments`: not the parent and not `@Argument` means the context
        // supplies it. This used to key off `@GraphQLContext` alone, which let a registered context
        // type pass schema build and then be dropped here, since there was no branch for it and no
        // `else` — `callBy` was simply handed a map without it.
        arguments[parameter] = batchContextValue(parameter, environment, context, function.name)
    }
    val raw =
        if (function.isSuspend) {
            function.callSuspendBy(arguments)
        } else {
            function.callBy(arguments)
        }
    return align(parents, raw)
}

private fun align(
    keys: List<Any>,
    raw: Any?,
): Map<Any, Any> =
    when (raw) {
        is Map<*, *> -> {
            buildMap {
                raw.forEach { (key, value) ->
                    if (key != null && value != null) put(key, value)
                }
            }
        }

        is List<*> -> {
            if (raw.size != keys.size) {
                throw GraphixException("@BatchMapping returned ${raw.size} values for ${keys.size} keys")
            }
            buildMap {
                keys.zip(raw).forEach { (key, value) ->
                    if (value != null) put(key, value)
                }
            }
        }

        else -> {
            throw GraphixException("@BatchMapping must return Map<Parent, T> or List<T>, got ${raw?.let { it::class.qualifiedName }}")
        }
    }
