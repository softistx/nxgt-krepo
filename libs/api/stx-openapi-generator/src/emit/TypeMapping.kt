package com.softistx.openapi.emit

import com.softistx.openapi.TypeRef
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT

/**
 * The spec types with no single obvious Kotlin equivalent.
 *
 * Which pair applies follows the serialization library the output targets, so it comes from
 * `com.softistx.openapi.models.ModelStyle` rather than from the client style.
 */
public class TypeStyle(
    public val instant: TypeName,
    public val localDate: TypeName,
    public val uuid: TypeName,
    public val freeForm: TypeName,
)

/** The one place a [TypeRef] becomes a Kotlin type, so every emitter agrees on the mapping. */
public fun typeNameOf(
    type: TypeRef,
    options: EmitOptions,
    style: TypeStyle,
): TypeName =
    when (type) {
        TypeRef.StringRef -> STRING
        TypeRef.IntRef -> INT
        TypeRef.LongRef -> LONG
        TypeRef.DoubleRef -> DOUBLE
        TypeRef.BooleanRef -> BOOLEAN
        TypeRef.InstantRef -> style.instant
        TypeRef.LocalDateRef -> style.localDate
        TypeRef.UuidRef -> style.uuid
        TypeRef.JsonObjectRef -> style.freeForm
        TypeRef.BinaryRef -> BYTE_ARRAY
        TypeRef.UnitRef -> UNIT
        is TypeRef.ListRef -> LIST.parameterizedBy(typeNameOf(type.element, options, style))
        is TypeRef.MapRef -> MAP.parameterizedBy(STRING, typeNameOf(type.value, options, style))
        is TypeRef.ModelRef -> ClassName(options.modelPackage, type.name)
        is TypeRef.ExternalRef -> ClassName.bestGuess(type.qualifiedName)
    }
