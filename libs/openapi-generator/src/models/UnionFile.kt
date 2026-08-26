package com.strange.openapi.models

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.UnionType
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.GENERATED_KDOC

/**
 * A `oneOf`/`anyOf` as a sealed interface, with its members implementing it.
 *
 * A discriminated union also gets a generated catch-all subtype, for the same reason a generated
 * enum gets a fallback entry: a server that deploys a variant this document does not list should
 * not break a client compiled against the older one. A union told apart by shape gets none — there
 * is no tag to put in it, and a payload matching nothing is genuinely undecodable.
 */
internal fun unionFile(
    model: UnionType,
    options: EmitOptions,
    style: ModelStyle,
): FileSpec {
    val binding = style.unionBinding
    val baseClass = ClassName(options.modelPackage, model.name)
    val subtypeNames = model.subtypes.associate { it.name to ClassName(options.modelPackage, it.name) }
    val fallbackClass = model.fallback?.let { ClassName(options.modelPackage, it) }

    val base =
        TypeSpec
            .interfaceBuilder(model.name)
            .addModifiers(KModifier.SEALED)
            .addKdoc(baseKdoc(model))
            .apply {
                model.discriminator?.let {
                    addProperty(
                        PropertySpec
                            .builder(it.name, STRING)
                            .addKdoc("Names which variant this is.")
                            .build(),
                    )
                }
                binding.decorateBase(this, model, subtypeNames, fallbackClass)
            }.build()

    return FileSpec
        .builder(options.modelPackage, model.name)
        // Only a fallback subtype reaches for experimental API; a deduced union needs no opt-in.
        .apply { if (fallbackClass != null) binding.decorateFile(this) }
        .addType(base)
        .apply {
            fallbackClass?.let { addType(fallbackType(model, it, baseClass, binding, style)) }
            binding.companions(model, baseClass, subtypeNames, fallbackClass).forEach { addType(it) }
        }.build()
}

private fun baseKdoc(model: UnionType) =
    buildString {
        append(GENERATED_KDOC)
        if (model.discriminator == null) {
            append("\n\nThe document declares no discriminator, so a payload is matched by the keys it carries.")
        }
    }

private fun fallbackType(
    model: UnionType,
    fallbackClass: ClassName,
    baseClass: ClassName,
    binding: UnionBinding,
    style: ModelStyle,
): TypeSpec {
    val discriminator = requireNotNull(model.discriminator) { "a union without a discriminator has no fallback" }
    return TypeSpec
        .classBuilder(fallbackClass)
        .addModifiers(KModifier.DATA)
        .addSuperinterface(baseClass)
        .addAnnotations(style.classAnnotations)
        .addKdoc(
            "A variant this client's document does not list.\n\n" +
                "It carries the tag that named it and nothing else: the rest of the payload belongs to " +
                "a shape this client cannot describe.",
        ).primaryConstructor(
            com.squareup.kotlinpoet.FunSpec
                .constructorBuilder()
                .addParameter(discriminator.name, STRING)
                .build(),
        ).addProperty(
            PropertySpec
                .builder(discriminator.name, STRING, KModifier.OVERRIDE)
                .initializer(discriminator.name)
                .build(),
        ).apply { binding.decorateFallback(this) }
        .build()
}
