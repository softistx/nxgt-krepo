package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import com.strange.graphix.execute.RegisteredLoader
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters

internal fun collectLoaders(instances: List<Any>): List<RegisteredLoader> {
    val result = mutableListOf<RegisteredLoader>()
    val seen = mutableSetOf<String>()
    instances.distinct().forEach { instance ->
        instance::class.memberFunctions.filter { it.hasAnnotation<Loader>() }.forEach { function ->
            if (function.instanceParameter == null) {
                throw GraphixException("@Loader ${function.name} is not a member function")
            }
            val keys =
                function.valueParameters.firstOrNull { !it.isGraphQLContext() }
                    ?: throw GraphixException("@Loader ${function.name} needs a List<K> parameter")
            keys.type.listElement()
                ?: throw GraphixException("@Loader ${function.name} keys must be List<K>")
            val extras = function.valueParameters.filter { it != keys && !it.isGraphQLContext() }
            if (extras.isNotEmpty()) {
                throw GraphixException("@Loader ${function.name} cannot have GraphQL arguments")
            }
            function.returnType.batchPayload()
            val name =
                function.findAnnotation<Loader>()?.name?.takeIf { it.isNotEmpty() }
                    ?: function.findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }
                    ?: function.name
            if (!seen.add(name)) {
                throw GraphixException("duplicate DataLoader '$name'")
            }
            result +=
                RegisteredLoader(
                    name = name,
                    instance = instance,
                    function = function,
                    keysParameter = keys,
                )
        }
    }
    return result
}

internal fun TypeFieldMeta.toRegisteredLoader(): RegisteredLoader =
    RegisteredLoader(
        name = loaderName,
        instance = instance,
        function = function,
        keysParameter = parentParameter,
    )

internal fun mergeLoaders(
    named: List<RegisteredLoader>,
    typeFields: List<TypeFieldMeta>,
): List<RegisteredLoader> {
    val result = named.toMutableList()
    val seen = named.map { it.name }.toMutableSet()
    typeFields.filter { it.batched }.forEach { field ->
        val loader = field.toRegisteredLoader()
        if (!seen.add(loader.name)) {
            throw GraphixException("duplicate DataLoader '${loader.name}'")
        }
        result += loader
    }
    return result
}

internal fun validateLoads(
    instances: List<Any>,
    loaderNames: Set<String>,
) {
    instances.distinct().forEach { instance ->
        instance::class.memberFunctions.forEach { function ->
            function.valueParameters.filter { it.isLoad() }.forEach { parameter ->
                val annotation = parameter.findAnnotation<Load>() ?: return@forEach
                val name = annotation.name.ifEmpty { parameter.name ?: return@forEach }
                if (name !in loaderNames) {
                    throw GraphixException("@Load '$name' on ${function.name} has no @Loader or @Batch")
                }
            }
        }
    }
}
