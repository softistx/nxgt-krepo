package com.softistx.openapi.parser

import com.softistx.openapi.TypeRef
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Resolving an OpenAPI schema to a [TypeRef].
 *
 * Kept apart from the rest of the parser because it is the one piece every other part leans on —
 * operations, parameters and models all reduce their schemas through here, and it is where the
 * document's `$ref` indirection stops.
 */
internal fun OpenAPI.typeOf(
    schema: Schema<*>?,
    where: String,
    seenRefs: Set<String> = emptySet(),
): TypeRef {
    if (schema == null) return TypeRef.JsonObjectRef
    schema.extensions.externalType(where)?.let { return TypeRef.ExternalRef(it) }
    schema.`$ref`?.let { ref ->
        val name = ref.substringAfterLast('/')
        val target = components?.schemas?.get(name)
        // A type the consumer owns is named by the extension, not by this generator, so it is read
        // before anything else the target says.
        target?.extensions?.externalType("schema $name")?.let { return TypeRef.ExternalRef(it) }
        // Only a schema that becomes a declaration keeps its name. A $ref to a scalar or array
        // alias (e.g. `Upload: {type: string, format: binary}`) must resolve to the underlying
        // type, or it would name a class that is never emitted.
        if (target != null && schemaKindOf(target) == null && ref !in seenRefs) {
            return typeOf(target, "$where -> $name", seenRefs + ref)
        }
        return TypeRef.ModelRef(modelNameOf(name))
    }

    // `oneOf: [Cat, {type: "null"}]` is another way of writing a nullable Cat. The nullability
    // is read separately, by isNullable(); what is left here is the type it wraps.
    schema.soleBranch()?.let { return typeOf(it, where, seenRefs) }

    // OpenAPI 3.1 allows `type` to be a set, and `["string", "null"]` is how it spells a nullable
    // string. The null carries no type information — it is read back by isNullable() — so the
    // meaningful entry is the other one, whichever order the document happens to list them in.
    val type = schema.type ?: schema.types?.firstOrNull { it != "null" } ?: schema.types?.firstOrNull()
    return when (type) {
        "array" -> {
            TypeRef.ListRef(typeOf(schema.items, "$where item", seenRefs))
        }

        "string" -> {
            when (schema.format) {
                "date-time" -> TypeRef.InstantRef
                "date" -> TypeRef.LocalDateRef
                "uuid" -> TypeRef.UuidRef
                "binary" -> TypeRef.BinaryRef
                else -> TypeRef.StringRef
            }
        }

        "integer" -> {
            if (schema.format == "int64") TypeRef.LongRef else TypeRef.IntRef
        }

        "number" -> {
            TypeRef.DoubleRef
        }

        "boolean" -> {
            TypeRef.BooleanRef
        }

        "object", null -> {
            // `additionalProperties` with a schema means open keys but typed values. Declared
            // properties win: a schema that has both is a class with an escape hatch, not a map.
            val values = schema.additionalProperties
            if (schema.properties.isNullOrEmpty() && values is Schema<*>) {
                TypeRef.MapRef(typeOf(values, "$where value", seenRefs))
            } else {
                TypeRef.JsonObjectRef
            }
        }

        else -> {
            throw OpenApiParseException("$where: unsupported schema type '$type'")
        }
    }
}

/** Follows a component `$ref` one level, for places where a schema's parts matter more than its name. */
internal fun OpenAPI.resolveSchema(
    schema: Schema<*>,
    where: String,
): Schema<*> {
    val ref = schema.`$ref` ?: return schema
    val name = ref.substringAfterLast('/')
    return components?.schemas?.get(name)
        ?: throw OpenApiParseException("$where: cannot resolve schema ${'$'}ref '$ref'")
}
