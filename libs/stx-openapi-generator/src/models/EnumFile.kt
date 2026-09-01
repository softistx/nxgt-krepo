package com.softistx.openapi.models

import com.softistx.openapi.EnumEntry
import com.softistx.openapi.EnumType
import com.softistx.openapi.TypeRef
import com.softistx.openapi.emit.EmitOptions
import com.softistx.openapi.emit.GENERATED_KDOC
import com.softistx.openapi.emit.typeNameOf
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

internal const val WIRE_VALUE = "wireValue"

internal const val FROM_WIRE_VALUE = "fromWireValue"

/**
 * A constrained schema, as a tolerant `enum class`.
 *
 * The wire value lives in a property rather than in an annotation, which is what lets an entry be
 * named freely — `in-progress` is not an identifier, and neither is `2xx`. It also gives both
 * styles one thing to bind to, and gives `toString` something correct to return, so an enum works
 * as a path, query or header parameter and not only inside a JSON body.
 */
internal fun enumFile(
    model: EnumType,
    options: EmitOptions,
    style: ModelStyle,
): FileSpec {
    val binding = style.enumBinding
    val wireType = typeNameOf(model.base, options, style.types)
    // The declaration's own name, so references to it resolve in-package instead of being imported.
    val enumClass = ClassName(options.modelPackage, model.name)
    val builder =
        TypeSpec
            .enumBuilder(model.name)
            .addKdoc(GENERATED_KDOC)
            .primaryConstructor(
                FunSpec.constructorBuilder().addParameter(WIRE_VALUE, wireType).build(),
            ).addProperty(
                PropertySpec
                    .builder(WIRE_VALUE, wireType)
                    .initializer(WIRE_VALUE)
                    .addKdoc("The value as the document writes it.")
                    .apply { binding.decorateWireValue(this) }
                    .build(),
            )

    model.entries.forEach { entry ->
        builder.addEnumConstant(
            entry.name,
            constant(entry, model.base)
                .toBuilder()
                .apply { entry.doc?.let { addKdoc("%L", it) } }
                .build(),
        )
    }
    builder.addEnumConstant(
        model.fallback.name,
        constant(model.fallback, model.base)
            .toBuilder()
            .addKdoc(
                "A value this client's document does not list.\n\n" +
                    "Its wire value is a sentinel no server accepts, so writing an object back " +
                    "unchanged fails there rather than silently replacing the real value.",
            ).build(),
    )

    builder
        .addFunction(
            FunSpec
                .builder("toString")
                .addModifiers(KModifier.OVERRIDE)
                .returns(STRING)
                .addStatement(if (model.base == TypeRef.StringRef) "return %N" else "return %N.toString()", WIRE_VALUE)
                .build(),
        ).addType(companionObject(model, enumClass, wireType, binding))
    binding.decorateEnum(builder, model)

    return FileSpec
        .builder(options.modelPackage, model.name)
        .addType(builder.build())
        .apply { binding.companions(model, enumClass, wireType).forEach { addType(it) } }
        .build()
}

private fun constant(
    entry: EnumEntry,
    base: TypeRef,
): TypeSpec =
    TypeSpec
        .anonymousClassBuilder()
        .apply {
            when (base) {
                TypeRef.StringRef -> addSuperclassConstructorParameter("%S", entry.wireValue)
                TypeRef.LongRef -> addSuperclassConstructorParameter("%LL", entry.wireValue)
                else -> addSuperclassConstructorParameter("%L", entry.wireValue)
            }
        }.build()

private fun companionObject(
    model: EnumType,
    enumClass: ClassName,
    wireType: TypeName,
    binding: EnumBinding,
): TypeSpec =
    TypeSpec
        .companionObjectBuilder()
        .addFunction(
            FunSpec
                .builder(FROM_WIRE_VALUE)
                .addKdoc("[%N] for any value this client's document does not list.", model.fallback.name)
                .addParameter(WIRE_VALUE, wireType)
                .returns(enumClass)
                .addStatement(
                    "return entries.firstOrNull { it.%N == %N } ?: %N",
                    WIRE_VALUE,
                    WIRE_VALUE,
                    model.fallback.name,
                ).apply { binding.decorateFactory(this) }
                .build(),
        ).build()
