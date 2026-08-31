package com.strange.graphix.schema

import graphql.language.InterfaceTypeDefinition
import graphql.language.UnionTypeDefinition
import graphql.schema.idl.TypeDefinitionRegistry

/**
 * What the SDL documents say about their abstract types. Kept out of [sdlSchema] so that file
 * stays about assembling the executable schema.
 */
internal fun TypeDefinitionRegistry.abstractTypeNames(): List<String> =
    getTypes(InterfaceTypeDefinition::class.java).map { it.name } +
        getTypes(UnionTypeDefinition::class.java).map { it.name }

/** Object types implementing the interface, or the members of the union. Empty when [name] is concrete. */
internal fun TypeDefinitionRegistry.implementorsOf(name: String): List<String> {
    val definition = types()[name] ?: return emptyList()
    return when (definition) {
        is InterfaceTypeDefinition -> getImplementationsOf(definition).map { it.name }
        is UnionTypeDefinition -> definition.memberTypes.mapNotNull { (it as? graphql.language.TypeName)?.name }
        else -> emptyList()
    }
}

internal fun TypeDefinitionRegistry.isUnion(name: String): Boolean = types()[name] is UnionTypeDefinition
