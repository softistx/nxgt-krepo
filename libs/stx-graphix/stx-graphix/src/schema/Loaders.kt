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
        instance::class.memberFunctions.filter { it.hasAnnotation<BatchLoading>() }.forEach { function ->
            if (function.instanceParameter == null) {
                throw GraphixException("@BatchLoading ${function.name} is not a member function")
            }
            val source =
                function.valueParameters.firstOrNull { !it.isGraphQLContext() }
                    ?: throw GraphixException("@BatchLoading ${function.name} needs a source: List<Parent> parameter")
            source.type.listElement()
                ?: throw GraphixException(
                    "@BatchLoading ${function.name} source must be List<Parent> so Graphix can batch — a single parent is N+1",
                )
            val extras = function.valueParameters.filter { it != source && !it.isGraphQLContext() }
            if (extras.isNotEmpty()) {
                throw GraphixException("@BatchLoading ${function.name} cannot have GraphQL arguments")
            }
            function.returnType.batchPayload()
            val name =
                function.findAnnotation<BatchLoading>()?.name?.takeIf { it.isNotEmpty() }
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
                    keysParameter = source,
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
