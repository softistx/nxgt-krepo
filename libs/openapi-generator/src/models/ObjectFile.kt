package com.strange.openapi.models

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
    val properties =
        model.fields.map { field ->
            val optionality = optionalityOf(field.type, field.required, field.nullable, field.default)
            val type = typeNameOf(field.type, options, style.types).copy(nullable = optionality.nullable)
            constructor.addParameter(
                ParameterSpec
                    .builder(field.name, type)
                    .apply {
                        if (field.name != field.wireName) addAnnotation(style.wireNameAnnotation(field.wireName))
                        optionality.defaultSource?.let { defaultValue(it) }
                    }.build(),
            )
            PropertySpec.builder(field.name, type).initializer(field.name).build()
        }
    val type =
        TypeSpec
            .classBuilder(model.name)
            .addModifiers(KModifier.DATA)
            .addAnnotations(style.classAnnotations)
            .addKdoc(GENERATED_KDOC)
            .primaryConstructor(constructor.build())
            .addProperties(properties)
            .build()
    return FileSpec.builder(options.modelPackage, model.name).addType(type).build()
}
