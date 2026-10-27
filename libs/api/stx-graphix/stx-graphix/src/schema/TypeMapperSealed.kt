@file:OptIn(ExperimentalSerializationApi::class)

package com.softistx.graphix.schema

import com.softistx.graphix.GraphixException
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLUnionType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * A sealed hierarchy is a GraphQL `interface` when its subclasses share properties and a
 * `union` when they do not. An open polymorphic type is neither: GraphQL needs a closed set.
 */
internal fun TypeMapper.abstractType(
    kType: KType,
    kind: PolymorphicKind,
): GraphQLOutputType {
    if (kind != PolymorphicKind.SEALED) {
        throw GraphixException(
            "open polymorphic $kType is not a GraphQL type — " +
                "GraphQL needs a closed set of possible types; make it a sealed interface",
        )
    }
    val hierarchy = kType.sealedHierarchy()
    return when (hierarchy.shape) {
        SealedShape.INTERFACE -> interfaceType(hierarchy)
        SealedShape.UNION -> unionType(hierarchy)
    }
}

private fun TypeMapper.interfaceType(hierarchy: SealedHierarchy): GraphQLOutputType {
    val name = hierarchy.name
    interfaces[name]?.let { return it }
    if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
    val builder =
        GraphQLInterfaceType
            .newInterface()
            .name(name)
            .description(hierarchy.base.graphQLDescription())
    // An interface reached through an intermediate sealed level implements the one above it.
    hierarchy.base.graphQLInterfaces().forEach { supertype ->
        val above = (supertype.classifier as KClass<*>).graphQLName()
        builder.withInterface(GraphQLTypeReference.typeRef(above))
        if (above !in interfaces && above !in building) mapOutput(supertype)
    }
    hierarchy.sharedProperties.forEach { property ->
        builder.field { field ->
            field
                .name(property.graphQLPropertyName())
                .description(property.graphQLDescription())
                .type(output(property.returnType, property.isGraphQLId()))
                .apply { property.graphQLDeprecation()?.let { deprecate(it) } }
        }
    }
    extraFields[name].orEmpty().forEach { extra ->
        builder.field(fieldDefinition(extra.function, extra.fieldName, output(extra.graphqlType, extra.function.isGraphQLId()), this))
    }
    building.remove(name)
    val type = builder.build().also { interfaces[name] = it }
    // Members are built after the interface is memoised, so their `implements` reference resolves.
    hierarchy.members.forEach { mapOutput(it) }
    return type
}

private fun TypeMapper.unionType(hierarchy: SealedHierarchy): GraphQLOutputType {
    val name = hierarchy.name
    unions[name]?.let { return it }
    if (!building.add(name)) return GraphQLTypeReference.typeRef(name)
    val members = hierarchy.members.map { mapOutput(it) }
    val builder =
        GraphQLUnionType
            .newUnionType()
            .name(name)
            .description(hierarchy.base.graphQLDescription())
    members.forEach { member ->
        when (member) {
            is GraphQLObjectType -> builder.possibleType(member)
            is GraphQLTypeReference -> builder.possibleType(member)
            else -> throw GraphixException("union '$name' member is not an object type: $member")
        }
    }
    building.remove(name)
    return builder.build().also { unions[name] = it }
}
