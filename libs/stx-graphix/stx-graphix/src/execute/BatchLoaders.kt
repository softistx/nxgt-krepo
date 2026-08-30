package com.strange.graphix.execute

import com.strange.graphix.GraphixException
import com.strange.graphix.schema.GraphQLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.valueParameters

internal data class RegisteredLoader(
    val name: String,
    val instance: Any,
    val function: KFunction<*>,
    val keysParameter: KParameter,
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
            DataLoaderFactory.newMappedDataLoader<Any, Any> { keys ->
                scope.future { loader.load(keys, context) }
            },
        )
    }
    return registry
}

private suspend fun RegisteredLoader.load(
    keys: Set<Any>,
    context: Map<KClass<*>, Any>,
): Map<Any, Any> {
    val arguments = LinkedHashMap<KParameter, Any?>()
    val instanceParameter =
        function.instanceParameter
            ?: error("${function.name} is not a member function")
    arguments[instanceParameter] = instance
    arguments[keysParameter] = keys.toList()
    function.valueParameters.forEach { parameter ->
        if (parameter.findAnnotation<GraphQLContext>() != null) {
            val classifier =
                parameter.type.classifier as? KClass<*>
                    ?: throw GraphixException("@GraphQLContext ${parameter.name} needs a class type")
            arguments[parameter] = context[classifier]
                ?: throw GraphixException("no ${classifier.qualifiedName} in the operation context")
        }
    }
    val raw =
        if (function.isSuspend) {
            function.callSuspendBy(arguments)
        } else {
            function.callBy(arguments)
        }
    return align(keys.toList(), raw)
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
