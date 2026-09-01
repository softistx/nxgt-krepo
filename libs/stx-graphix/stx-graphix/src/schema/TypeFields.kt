package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters

internal data class TypeFieldMeta(
    val instance: Any,
    val function: KFunction<*>,
    val parentName: String,
    val parentType: KType,
    val parentParameter: KParameter,
    val argumentParameters: List<KParameter>,
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
        val functions =
            instance::class.memberFunctions.filter {
                it.hasAnnotation<SchemaMapping>() || it.hasAnnotation<BatchMapping>()
            }
        if (functions.isEmpty()) {
            throw GraphixException("${instance::class.qualifiedName} has no @SchemaMapping or @BatchMapping functions")
        }
        functions.forEach { function ->
            if (function.hasAnnotation<SchemaMapping>() && function.hasAnnotation<BatchMapping>()) {
                throw GraphixException("@SchemaMapping and @BatchMapping cannot both sit on ${function.name}")
            }
            if (function.instanceParameter == null) {
                throw GraphixException(
                    "@${if (function.hasAnnotation<BatchMapping>()) "BatchMapping" else "SchemaMapping"} ${function.name} is not a member function",
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
    val batched = function.hasAnnotation<BatchMapping>()
    val kind = if (batched) "BatchMapping" else "SchemaMapping"
    val parentParameter =
        function.valueParameters.firstOrNull { !it.isGraphQLContext() && !it.isDataFetchingEnvironment() }
            ?: throw GraphixException("@$kind ${function.name} needs a parent parameter")
    val parentType =
        if (batched) {
            parentParameter.type.listElement()
                ?: throw GraphixException("@BatchMapping ${function.name} parent must be List<T>")
        } else {
            parentParameter.type
        }
    val parentClass =
        parentType.classifier as? KClass<*>
            ?: throw GraphixException("@$kind ${function.name} parent must be a class")
    val parentName = function.mappingTypeName() ?: parentClass.graphQLName()
    val fieldName = function.mappingFieldName()
    if (!seen.add(parentName to fieldName)) {
        throw GraphixException("duplicate field '$fieldName' on $parentName — use @SchemaMapping or @BatchMapping, not both")
    }
    function.requireArgumentAnnotations(parentParameter)
    val graphqlType =
        if (batched) {
            function.returnType.batchPayload()
        } else {
            function.returnType.unwrapAsync()
        }
    return TypeFieldMeta(
        instance = instance,
        function = function,
        parentName = parentName,
        parentType = parentType,
        parentParameter = parentParameter,
        argumentParameters = function.valueParameters.filter { it.isArgument() },
        fieldName = fieldName,
        batched = batched,
        loaderName = fieldName,
        graphqlType = graphqlType,
    )
}

private fun KFunction<*>.mappingTypeName(): String? =
    findAnnotation<SchemaMapping>()?.typeName?.takeIf { it.isNotEmpty() }
        ?: findAnnotation<BatchMapping>()?.typeName?.takeIf { it.isNotEmpty() }

private fun KFunction<*>.mappingFieldName(): String =
    findAnnotation<SchemaMapping>()?.field?.takeIf { it.isNotEmpty() }
        ?: findAnnotation<BatchMapping>()?.field?.takeIf { it.isNotEmpty() }
        ?: name

internal fun KType.listElement(): KType? {
    val classifier = classifier as? KClass<*> ?: return null
    if (!classifier.isSubclassOf(List::class)) return null
    return arguments.singleOrNull()?.type
}

internal fun KType.unwrapAsync(): KType {
    val classifier = classifier as? KClass<*> ?: return this
    if (!classifier.isSubclassOf(java.util.concurrent.CompletionStage::class)) return this
    return arguments.singleOrNull()?.type
        ?: throw GraphixException("CompletionStage needs a type argument: $this")
}

internal fun KType.batchPayload(): KType {
    val classifier =
        classifier as? KClass<*>
            ?: throw GraphixException("@BatchMapping return type must be Map<Parent, T> or List<T>, got $this")
    return when {
        classifier.isSubclassOf(Map::class) -> {
            arguments.getOrNull(1)?.type
                ?: throw GraphixException("@BatchMapping Map needs a value type: $this")
        }

        classifier.isSubclassOf(List::class) -> {
            arguments.singleOrNull()?.type
                ?: throw GraphixException("@BatchMapping List needs an element type: $this")
        }

        else -> {
            throw GraphixException("@BatchMapping must return Map<Parent, T> or List<T>, got $this")
        }
    }
}
