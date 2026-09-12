package com.softistx.openapi.models

import com.softistx.openapi.ObjectType
import com.softistx.openapi.emit.EmitOptions
import com.softistx.openapi.emit.GENERATED_KDOC
import com.softistx.openapi.emit.optionalityOf
import com.softistx.openapi.emit.typeNameOf
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

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
                    field.doc?.let { addKdoc("%L", it) }
                    if (field.deprecated) {
                        addAnnotation(
                            deprecated(field.deprecatedReason ?: "This property is deprecated in the OpenAPI document."),
                        )
                    }
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
            .addAnnotation(style.unknownFieldTolerance)
            .addKdoc(kdoc(model))
            .apply {
                if (model.deprecated) {
                    addAnnotation(
                        deprecated(model.deprecatedReason ?: "This schema is deprecated in the OpenAPI document."),
                    )
                }
            }.apply { model.implements.forEach { addSuperinterface(ClassName(options.modelPackage, it)) } }
            .primaryConstructor(constructor.build())
            .addProperties(properties)
            .build()
    return FileSpec
        .builder(options.modelPackage, model.name)
        // Both the constant discriminator and unknown-field tolerance are experimental in kotlinx.
        .apply { style.decorateFile(this) }
        .addType(type)
        .build()
}

/** The schema's own prose above the boilerplate, so the document's explanation survives. */
private fun kdoc(model: ObjectType) = model.doc?.let { "$it\n\n$GENERATED_KDOC" } ?: GENERATED_KDOC

private fun deprecated(message: String) = AnnotationSpec.builder(Deprecated::class).addMember("%S", message).build()
