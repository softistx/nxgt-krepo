@file:OptIn(ExperimentalSerializationApi::class)

package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLTypeReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlin.reflect.KClass
import kotlin.reflect.KType

internal fun TypeMapper.objectType(
    kType: KType,
    descriptor: SerialDescriptor,
): GraphQLOutputType {
    val kClass = kClassOf(kType)
    val name = kClass.graphQLName()
    outputs[name]?.let { return it }
    if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
    val builder =
        GraphQLObjectType
            .newObject()
            .name(name)
            .description(kClass.graphQLDescription())
    val inherited = mutableListOf<TypeFieldMeta>()
    kClass.graphQLInterfaces().forEach { supertype ->
        val interfaceName = (supertype.classifier as KClass<*>).graphQLName()
        builder.withInterface(GraphQLTypeReference.typeRef(interfaceName))
        implementors.getOrPut(interfaceName) { linkedSetOf() } += name
        // An object type must declare every field of the interfaces it implements.
        inherited += extraFields[interfaceName].orEmpty()
        // The interface has to exist in the schema for the reference to resolve.
        if (interfaceName !in interfaces && interfaceName !in building) mapOutput(supertype)
    }
    val propertyNames = mutableSetOf<String>()
    properties(kClass, descriptor).forEach { (elementName, elementType, property) ->
        val fieldName = property.graphQLPropertyName()
        propertyNames += fieldName
        // graphql-java's default fetcher reads the *GraphQL* name off the source object, and
        // a renamed property is not there under that name.
        if (fieldName != property.name) renames += PropertyRename(name, fieldName, property.name)
        builder.field { field ->
            field
                .name(fieldName)
                .description(property.graphQLDescription())
                .type(output(elementType, property.isGraphQLId()))
                .apply { property.graphQLDeprecation()?.let { deprecate(it) } }
        }
    }
    val own = extraFields[name].orEmpty()
    (own + inherited.filterNot { extra -> own.any { it.fieldName == extra.fieldName } }).forEach { extra ->
        if (extra.fieldName in propertyNames) {
            throw GraphixException("duplicate field '${extra.fieldName}' on $name")
        }
        propertyNames += extra.fieldName
        builder.field(
            fieldDefinition(
                extra.function,
                extra.fieldName,
                output(extra.graphqlType, extra.function.isGraphQLId()),
                this,
            ),
        )
    }
    building.remove(name)
    return builder.build().also { outputs[name] = it }
}

internal fun TypeMapper.enumType(
    kType: KType,
    descriptor: SerialDescriptor,
): GraphQLEnumType {
    val kClass = kClassOf(kType)
    val name = kClass.graphQLName()
    enums[name]?.let { return it }
    val builder =
        GraphQLEnumType
            .newEnum()
            .name(name)
            .description(kClass.graphQLDescription())
    descriptor.elementNames.forEach { builder.value(it) }
    return builder.build().also { enums[name] = it }
}
