package com.strange.openapi.models

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.ObjectType
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.GENERATED_KDOC
import com.strange.openapi.emit.optionalityOf
import com.strange.openapi.emit.typeNameOf

/** A schema with declared properties, as a data class. */
internal fun objectFile(
    model: ObjectType,
    options: EmitOptions,
    style: ModelStyle,
): FileSpec {
    val constructor = FunSpec.constructorBuilder()
    // A field the union base already declares is not a default: it identifies the variant, so it is
    // written always and its property overrides the one on the interface.
    // Defaulted parameters last, so a caller can still write positional arguments. A union
    // subtype makes this load-bearing: its discriminator always has a default and is usually the
    // first property the document declares.
    val ordered = model.fields.sortedBy { it.constant != null || (!it.required && it.default == null) || it.default != null }
    val properties =
        ordered.map { field ->
            val optionality = optionalityOf(field.type, field.required, field.nullable, field.default)
            val type = typeNameOf(field.type, options, style.types).copy(nullable = optionality.nullable)
            constructor.addParameter(
                ParameterSpec
                    .builder(field.name, type)
                    .apply {
                        if (field.name != field.wireName) addAnnotation(style.wireNameAnnotation(field.wireName))
                        when {
                            field.constant != null -> defaultValue("%S", field.constant)
                            else -> optionality.defaultSource?.let { defaultValue(it) }
                        }
                    }.build(),
            )
            PropertySpec
                .builder(field.name, type)
                .apply {
                    if (field.overrides) addModifiers(KModifier.OVERRIDE)
                    if (field.constant != null) style.unionBinding.decorateConstant(this)
                }.initializer(field.name)
                .build()
        }
    val type =
        TypeSpec
            .classBuilder(model.name)
            .addModifiers(KModifier.DATA)
            .addAnnotations(style.classAnnotations)
            .addKdoc(GENERATED_KDOC)
            .apply { model.implements.forEach { addSuperinterface(ClassName(options.modelPackage, it)) } }
            .primaryConstructor(constructor.build())
            .addProperties(properties)
            .build()
    return FileSpec
        .builder(options.modelPackage, model.name)
        .apply { if (model.fields.any { field -> field.constant != null }) style.unionBinding.decorateFile(this) }
        .addType(type)
        .build()
}
