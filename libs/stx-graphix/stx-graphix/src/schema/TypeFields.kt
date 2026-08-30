package com.strange.graphix.schema

import com.strange.graphix.GraphixException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters

internal data class TypeFieldMeta(
    val instance: Any,
    val function: KFunction<*>,
    val parentName: String,
    val parentType: KType,
    val parentParameter: KParameter,
    val fieldName: String,
    val batched: Boolean,
    val loaderName: String,
    val graphqlType: KType,
)

internal fun collectTypeFields(instances: List<Any>): List<TypeFieldMeta> {
    if (instances.isEmpty()) return emptyList()
    val result = mutableListOf<TypeFieldMeta>()
    val seen = mutableSetOf<Pair<String, String>>()
    instances.forEach { instance ->
        val functions = instance::class.memberFunctions.filter { it.hasAnnotation<Field>() || it.hasAnnotation<Batch>() }
        if (functions.isEmpty()) {
            throw GraphixException("${instance::class.qualifiedName} has no @Field or @Batch functions")
        }
        functions.forEach { function ->
            if (function.hasAnnotation<Field>() && function.hasAnnotation<Batch>()) {
                throw GraphixException("@Field and @Batch cannot both sit on ${function.name}")
            }
            if (function.instanceParameter == null) {
                throw GraphixException(
                    "@${if (function.hasAnnotation<Batch>()) "Batch" else "Field"} ${function.name} is not a member function",
                )
            }
            result += typeField(instance, function, seen)
        }
    }
    return result
}

private fun typeField(
    instance: Any,
    function: KFunction<*>,
    seen: MutableSet<Pair<String, String>>,
): TypeFieldMeta {
    val batched = function.hasAnnotation<Batch>()
    val parentParameter =
        function.valueParameters.firstOrNull { !it.isGraphQLContext() }
            ?: throw GraphixException("@${if (batched) "Batch" else "Field"} ${function.name} needs a parent parameter")
    val parentType =
        if (batched) {
            parentParameter.type.listElement()
                ?: throw GraphixException("@Batch ${function.name} parent must be List<T>")
        } else {
            parentParameter.type
        }
    val parentClass =
        parentType.classifier as? KClass<*>
            ?: throw GraphixException("@${if (batched) "Batch" else "Field"} ${function.name} parent must be a class")
    val parentName = parentClass.graphQLName()
    val fieldName = function.typeFieldName()
    if (!seen.add(parentName to fieldName)) {
        throw GraphixException("duplicate field '$fieldName' on $parentName")
    }
    if (batched) {
        val extras = function.valueParameters.filter { it != parentParameter && !it.isGraphQLContext() }
        if (extras.isNotEmpty()) {
            throw GraphixException("@Batch ${function.name} cannot have GraphQL arguments")
        }
    }
    val graphqlType = if (batched) function.returnType.batchPayload() else function.returnType
    return TypeFieldMeta(
        instance = instance,
        function = function,
        parentName = parentName,
        parentType = parentType,
        parentParameter = parentParameter,
        fieldName = fieldName,
        batched = batched,
        loaderName = "$parentName.$fieldName",
        graphqlType = graphqlType,
    )
}

internal fun KFunction<*>.typeFieldName(): String {
    findAnnotation<Field>()?.name?.takeIf { it.isNotEmpty() }?.let { return it }
    findAnnotation<Batch>()?.name?.takeIf { it.isNotEmpty() }?.let { return it }
    findAnnotation<GraphQLName>()?.value?.takeIf { it.isNotEmpty() }?.let { return it }
    return name
}

internal fun KType.listElement(): KType? {
    val classifier = classifier as? KClass<*> ?: return null
    if (!List::class.java.isAssignableFrom(classifier.java)) return null
    return arguments.singleOrNull()?.type
}

internal fun KType.batchPayload(): KType {
    val classifier =
        classifier as? KClass<*>
            ?: throw GraphixException("@Batch return type must be Map<Parent, T> or List<T>, got $this")
    return when {
        Map::class.java.isAssignableFrom(classifier.java) -> {
            arguments.getOrNull(1)?.type
                ?: throw GraphixException("@Batch Map needs a value type: $this")
        }

        List::class.java.isAssignableFrom(classifier.java) -> {
            arguments.singleOrNull()?.type
                ?: throw GraphixException("@Batch List needs an element type: $this")
        }

        else -> {
            throw GraphixException("@Batch must return Map<Parent, T> or List<T>, got $this")
        }
    }
}
