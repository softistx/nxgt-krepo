package com.strange.openapi.models

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.TypeRef
import com.strange.openapi.ValueClassType
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.GENERATED_KDOC
import com.strange.openapi.emit.typeNameOf

internal const val VALUE = "value"

/**
 * A scalar alias, as a `@JvmInline value class`.
 *
 * Neither library needs an annotation of its own here — kotlinx binds it through `@Serializable`
 * on the wrapper and Jackson's Kotlin module handles value classes natively — which was settled by
 * round-tripping the exact shape below through both, not from memory. The two write identical bytes.
 *
 * `toString` returns the value underneath for the same reason a generated enum's does: a path,
 * query or header argument is converted with `toString`, and the default would send
 * `OrderId(value=o-1)`.
 */
internal fun valueClassFile(
    model: ValueClassType,
    options: EmitOptions,
    style: ModelStyle,
): FileSpec {
    val base = typeNameOf(model.base, options, style.types)
    val type =
        TypeSpec
            .classBuilder(model.name)
            .addModifiers(KModifier.VALUE)
            .addAnnotation(JvmInline::class)
            .addAnnotations(style.classAnnotations)
            .addKdoc(model.doc?.let { "$it\n\n$GENERATED_KDOC" } ?: GENERATED_KDOC)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter(VALUE, base).build())
            .addProperty(PropertySpec.builder(VALUE, base).initializer(VALUE).build())
            .addFunction(
                FunSpec
                    .builder("toString")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(STRING)
                    .addStatement(if (model.base == TypeRef.StringRef) "return %N" else "return %N.toString()", VALUE)
                    .build(),
            ).build()
    return FileSpec.builder(options.modelPackage, model.name).addType(type).build()
}
