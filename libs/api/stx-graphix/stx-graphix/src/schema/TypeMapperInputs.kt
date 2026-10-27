package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import graphql.Directives
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLTypeReference
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

internal fun TypeMapper.inputObjectType(
    kType: KType,
    descriptor: SerialDescriptor,
): GraphQLInputType {
    val kClass = kClassOf(kType)
    val name = inputName(kClass)
    inputs[name]?.let { return it }
    if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
    val oneOf = kClass.findAnnotation<GraphQLOneOf>() != null
    val builder =
        GraphQLInputObjectType
            .newInputObject()
            .name(name)
            .description(kClass.graphQLDescription())
    if (oneOf) builder.withDirective(Directives.OneOfDirective)
    properties(kClass, descriptor).forEach { (elementName, elementType, property) ->
        refuseArgumentOnInputField(kClass, property)
        val fieldName = property.graphQLPropertyName()
        val default = property.graphQLDefault("$name.$fieldName")
        if (oneOf && default != null) {
            throw GraphixException(
                "$name is @GraphQLOneOf, so '$fieldName' may not have a @GraphQLDefault",
            )
        }
        val argumentType =
            input(elementType, property.isGraphQLId()).let { type ->
                // A GraphQL default keeps the NonNull: `size: Int! = 1` is what a Kotlin default means.
                if (default == null && constructorOptional(kClass, property.name) && type is GraphQLNonNull) {
                    type.wrappedType as GraphQLInputType
                } else {
                    type
                }
            }
        if (oneOf && argumentType is GraphQLNonNull) {
            throw GraphixException(
                "$name is @GraphQLOneOf but '$fieldName' is not nullable — " +
                    "every field of a oneOf input object must be, so a caller can send exactly one",
            )
        }
        val field =
            GraphQLInputObjectField
                .newInputObjectField()
                .name(fieldName)
                .description(property.graphQLDescription())
                .type(argumentType)
                .apply { if (default != null) defaultValueLiteral(default) }
        property.graphQLDeprecation()?.let { reason ->
            refuseRequiredDeprecation("input field '$fieldName' of $name", argumentType, default != null)
            field.deprecate(reason)
        }
        builder.field(field.build())
    }
    building.remove(name)
    return builder.build().also { inputs[name] = it }
}

/**
 * GraphQL forbids one name as both object and input object. When [kClass] already mapped
 * as output, the input is `{name}Input`. An explicit `@GraphQLName("…Input")` is left alone.
 */
private fun TypeMapper.inputName(kClass: KClass<*>): String {
    val name = kClass.graphQLName()
    if (name.endsWith("Input")) return name
    if (name in outputs || name in interfaces || name in unions) return name + "Input"
    return name
}

/**
 * `@Argument` marks a resolver parameter. The input object is already that argument;
 * its fields are not.
 */
private fun refuseArgumentOnInputField(
    kClass: KClass<*>,
    property: kotlin.reflect.KProperty<*>,
) {
    val parameter = kClass.primaryConstructor?.valueParameters?.find { it.name == property.name }
    val marked =
        parameter?.findAnnotation<Argument>() != null ||
            property.findAnnotation<Argument>() != null
    if (marked) {
        throw GraphixException(
            "${kClass.simpleName}.${property.name} must not be @Argument — " +
                "the input object is the argument, its fields are not",
        )
    }
}

/**
 * A Kotlin default on the primary constructor is still a GraphQL `NonNull` unless unwrapped.
 * graphql-java has no constructor defaults.
 */
private fun constructorOptional(
    kClass: KClass<*>,
    propertyName: String,
): Boolean =
    kClass.primaryConstructor
        ?.valueParameters
        ?.find { it.name == propertyName }
        ?.isOptional == true
