package com.strange.openapi.models

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.ApiModel
import com.strange.openapi.ModelType
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.GENERATED_KDOC
import com.strange.openapi.emit.typeNameOf

/**
 * One data class per component schema, in the style the caller's serializer needs.
 *
 * Every emitter goes through here, so a Ktorfit client and a Spring client describe the same
 * document with the same class and property names — only the annotations differ.
 */
internal fun modelFiles(
    model: ApiModel,
    options: EmitOptions,
    style: ModelStyle,
): List<FileSpec> = model.models.map { modelFile(it, options, style) }

private fun modelFile(
    model: ModelType,
    options: EmitOptions,
    style: ModelStyle,
): FileSpec {
    val constructor = FunSpec.constructorBuilder()
    val properties =
        model.fields.map { field ->
            // Optional fields are nullable and default to null, so a spec that gains a field does
            // not break callers that were compiled against the version before it.
            val type = typeNameOf(field.type, options, style.types).copy(nullable = !field.required)
            constructor.addParameter(
                ParameterSpec
                    .builder(field.name, type)
                    .apply {
                        if (field.name != field.wireName) addAnnotation(style.wireNameAnnotation(field.wireName))
                        if (!field.required) defaultValue("null")
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
