package com.softistx.openapi.parser

import com.softistx.openapi.TypeRef
import com.softistx.openapi.ValueClassType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

/**
 * Turning a scalar alias marked `x-kotlin-value-class` into a [ValueClassType].
 *
 * Only a scalar. A `value class` holds exactly one value, so a schema with properties has nothing
 * to be a wrapper of — and a document that asks anyway is stating something it cannot mean, which
 * is worth a build failure rather than a class that ignores half of what it says.
 */
internal fun OpenAPI.valueClassTypeOf(
    schema: Schema<*>,
    name: String,
    where: String,
): ValueClassType {
    if (!schema.enum.isNullOrEmpty()) {
        throw OpenApiParseException(
            "$where: ${Ext.VALUE_CLASS} and `enum` ask for two different declarations of the same " +
                "schema. A constrained set of values is already a type of its own.",
        )
    }
    val base = typeOf(schema, where)
    if (base !in WRAPPABLE) {
        throw OpenApiParseException(
            "$where: ${Ext.VALUE_CLASS} needs a scalar schema, but this one is $base. " +
                "A value class wraps exactly one value.",
        )
    }
    return ValueClassType(name = name, base = base, doc = schema.doc())
}

private val WRAPPABLE =
    setOf(
        TypeRef.StringRef,
        TypeRef.IntRef,
        TypeRef.LongRef,
        TypeRef.DoubleRef,
        TypeRef.BooleanRef,
        TypeRef.InstantRef,
        TypeRef.LocalDateRef,
        TypeRef.UuidRef,
    )
