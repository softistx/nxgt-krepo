package com.softistx.openapi.parser

import com.softistx.openapi.ModelType
import io.swagger.v3.oas.models.OpenAPI

/**
 * Turning the document's component schemas into [ModelType]s.
 *
 * Unions are resolved first, because a subtype cannot be parsed until its union is known: the
 * `oneOf` is what says a hierarchy is closed, and it lives on the base rather than on any member.
 */
internal fun OpenAPI.parseModels(): List<ModelType> {
    val memberships = unionMemberships()
    return components
        ?.schemas
        .orEmpty()
        .mapNotNull { (name, schema) ->
            if (schema.extensions.isExcluded("schema $name")) return@mapNotNull null
            when (schemaKindOf(schema)) {
                SchemaKind.Enum -> enumTypeOf(schema, modelNameOf(name), "model $name")

                SchemaKind.Union -> unionTypeOf(schema, modelNameOf(name), "model $name")

                SchemaKind.ValueClass -> valueClassTypeOf(schema, modelNameOf(name), "model $name")

                SchemaKind.Object -> objectTypeOf(schema, name, "model $name", memberships)

                // A schema that becomes no declaration is carried as its underlying type instead.
                null -> null
            }
        }.sortedBy { it.name }
        .requireDistinctNames()
}

/** Which schemas are members of which unions, keyed by the member's own component name. */
private fun OpenAPI.unionMemberships(): Map<String, List<UnionMembership>> {
    val memberships = mutableMapOf<String, MutableList<UnionMembership>>()
    components?.schemas.orEmpty().forEach { (baseSchemaName, schema) ->
        if (schemaKindOf(schema) != SchemaKind.Union) return@forEach
        val union = unionTypeOf(schema, modelNameOf(baseSchemaName), "model $baseSchemaName")
        unionMembersOf(schema).orEmpty().forEachIndexed { index, member ->
            memberships
                .getOrPut(member) { mutableListOf() }
                .add(
                    UnionMembership(
                        baseName = baseSchemaName,
                        baseSchemaName = baseSchemaName,
                        discriminatorWireName = union.discriminator?.wireName,
                        wireValue = union.subtypes[index].wireValue,
                    ),
                )
        }
    }
    return memberships
}
