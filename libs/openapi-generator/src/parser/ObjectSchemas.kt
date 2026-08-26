package com.strange.openapi.parser

import com.strange.openapi.Field
import com.strange.openapi.ObjectType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Turning an object schema — including one composed with `allOf` — into an [ObjectType].
 *
 * `allOf` is flattened rather than turned into an inheritance chain. Kotlin data classes cannot
 * inherit constructor properties, so extracting an interface would emit every field twice anyway;
 * the only interface a generated model set owns is a union base, which is anchored to a real schema.
 */
internal fun OpenAPI.objectTypeOf(
    schema: Schema<*>,
    name: String,
    where: String,
    memberships: Map<String, List<UnionMembership>> = emptyMap(),
): ObjectType {
    val unions = memberships[name].orEmpty()
    val discriminators = unions.mapNotNull { it.discriminatorWireName }.toSet()
    val composed = flatten(schema, where, skipping = unions.mapTo(mutableSetOf()) { it.baseSchemaName })
    val fields =
        composed.properties.map { (propertyName, propertySchema) ->
            val tag = unions.firstOrNull { it.discriminatorWireName == propertyName }
            Field(
                name =
                    propertySchema.extensions.kotlinName("$where property '$propertyName'")
                        ?: Naming.propertyName(propertyName),
                wireName = propertyName,
                type = typeOf(propertySchema, "$where property '$propertyName'"),
                required = propertyName in composed.required || propertyName in discriminators,
                nullable = propertySchema.isNullable("$where property '$propertyName'"),
                default = propertySchema.defaultLiteral(),
                doc = propertySchema.doc(),
                deprecated = propertySchema.deprecated == true,
                deprecatedReason = propertySchema.extensions.deprecatedReason("$where property '$propertyName'"),
                overrides = propertyName in discriminators,
                constant = tag?.wireValue,
            )
        }
    // A union's discriminator is declared on the base, so a member that never mentions it still
    // has to carry it — otherwise the sealed interface has an unimplemented property.
    val declared = fields.mapTo(mutableSetOf()) { it.wireName }
    val implied =
        unions
            .filter { it.discriminatorWireName != null && it.discriminatorWireName !in declared }
            .map { union ->
                Field(
                    name = Naming.propertyName(union.discriminatorWireName!!),
                    wireName = union.discriminatorWireName,
                    type = com.strange.openapi.TypeRef.StringRef,
                    required = true,
                    overrides = true,
                    constant = union.wireValue,
                )
            }
    return ObjectType(
        name = modelNameOf(name),
        fields = fields + implied,
        doc = schema.doc(),
        deprecated = schema.deprecated == true,
        deprecatedReason = schema.extensions.deprecatedReason(where),
        implements = unions.map { modelNameOf(it.baseName) },
    )
}

/** One schema's membership of one union, as the object parser needs to see it. */
internal data class UnionMembership(
    val baseName: String,
    val baseSchemaName: String,
    val discriminatorWireName: String?,
    val wireValue: String?,
)

private class Composed(
    val properties: Map<String, Schema<*>>,
    val required: Set<String>,
)

/**
 * Merges an `allOf` chain and the schema's own properties into one set.
 *
 * Every branch applies, so `required` is their union: a property optional in one branch and
 * required in another is required. Two branches declaring the same property with different types
 * is a document contradiction and fails — letting the last branch win is how a generated class
 * silently acquires the wrong shape.
 */
private fun OpenAPI.flatten(
    schema: Schema<*>,
    where: String,
    skipping: Set<String>,
    seen: Set<String> = emptySet(),
): Composed {
    val properties = linkedMapOf<String, Schema<*>>()
    val required = mutableSetOf<String>()

    // One merge path, so a conflict is caught wherever the property came from — an inline branch,
    // a $ref'd one, or the schema's own properties.
    fun merge(
        propertyName: String,
        propertySchema: Schema<*>,
    ) {
        val existing =
            properties[propertyName] ?: run {
                properties[propertyName] = propertySchema
                return
            }
        val existingType = typeOf(existing, where)
        val incomingType = typeOf(propertySchema, where)
        if (existingType != incomingType) {
            throw OpenApiParseException(
                "$where: allOf branches disagree about property '$propertyName' " +
                    "($existingType and $incomingType)",
            )
        }
    }

    schema.allOf.orEmpty().forEach { branch ->
        val ref = branch.`$ref`
        if (ref == null) {
            branch.properties.orEmpty().forEach { (name, propertySchema) -> merge(name, propertySchema) }
            required += branch.required.orEmpty()
            return@forEach
        }
        val target = ref.substringAfterLast('/')
        // The union base contributes only the discriminator, which is emitted as an override.
        if (target in skipping || target in seen) return@forEach
        val resolved =
            components?.schemas?.get(target)
                ?: throw OpenApiParseException("$where: cannot resolve allOf ${'$'}ref '$ref'")
        val nested = flatten(resolved, "$where -> $target", skipping, seen + target)
        nested.properties.forEach { (name, propertySchema) -> merge(name, propertySchema) }
        required += nested.required
    }
    schema.properties.orEmpty().forEach { (name, propertySchema) -> merge(name, propertySchema) }
    required += schema.required.orEmpty()
    return Composed(properties, required)
}
