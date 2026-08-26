package com.strange.openapi.parser

import com.strange.openapi.OpenApiParseException
import com.strange.openapi.TypeRef
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Resolving an OpenAPI schema to a [TypeRef].
 *
 * Kept apart from the rest of the parser because it is the one piece every other part leans on —
 * operations, parameters and models all reduce their schemas through here, and it is where the
 * document's `$ref` indirection stops.
 */
internal fun OpenAPI.typeOf(schema: Schema<*>?, where: String, seenRefs: Set<String> = emptySet()): TypeRef {
    if (schema == null) return TypeRef.JsonObjectRef
    schema.`$ref`?.let { ref ->
        val name = ref.substringAfterLast('/')
        val target = components?.schemas?.get(name)
        // Only object schemas become generated classes. A $ref to a scalar or array alias
        // (e.g. `Upload: {type: string, format: binary}`) must resolve to the underlying type,
        // or it would name a class that is never emitted.
        if (target != null && target.properties.isNullOrEmpty() && ref !in seenRefs) {
            return typeOf(target, "$where -> $name", seenRefs + ref)
        }
        return TypeRef.ModelRef(Naming.pascal(name))
    }

    // OpenAPI 3.1 allows `type` to be a set; 3.0 uses a single value.
    val type = schema.type ?: schema.types?.firstOrNull()
    return when (type) {
        "array" -> TypeRef.ListRef(typeOf(schema.items, "$where item", seenRefs))
        "string" -> when (schema.format) {
            "date-time" -> TypeRef.InstantRef
            "binary" -> TypeRef.BinaryRef
            else -> TypeRef.StringRef
        }
        "integer" -> if (schema.format == "int64") TypeRef.LongRef else TypeRef.IntRef
        "number" -> TypeRef.DoubleRef
        "boolean" -> TypeRef.BooleanRef
        "object", null -> TypeRef.JsonObjectRef
        else -> throw OpenApiParseException("$where: unsupported schema type '$type'")
    }
}

/** Follows a component `$ref` one level, for places where a schema's parts matter more than its name. */
internal fun OpenAPI.resolveSchema(schema: Schema<*>, where: String): Schema<*> {
    val ref = schema.`$ref` ?: return schema
    val name = ref.substringAfterLast('/')
    return components?.schemas?.get(name)
        ?: throw OpenApiParseException("$where: cannot resolve schema ${'$'}ref '$ref'")
}
