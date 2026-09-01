package com.softistx.openapi.models

import com.softistx.openapi.UnionType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * kotlinx selects the subtype through a generated content-polymorphic serializer.
 *
 * Its built-in polymorphism cannot be used: it writes a class discriminator of its own, and a
 * document that declares the discriminator as a property would then see it twice — kotlinx refuses
 * outright with *"cannot be serialized as base class … because it has property name that conflicts
 * with JSON class discriminator"*. Selecting from the content sidesteps that, and leaves the
 * property to write itself.
 */
internal object KotlinxUnionBinding : UnionBinding {
    override fun decorateBase(
        builder: TypeSpec.Builder,
        model: UnionType,
        subtypeNames: Map<String, ClassName>,
        fallback: ClassName?,
    ) {
        builder.addAnnotation(
            AnnotationSpec.builder(SERIALIZABLE).addMember("with = %N::class", serializerName(model)).build(),
        )
    }

    override fun decorateFallback(builder: TypeSpec.Builder) {
        // Every key but the discriminator belongs to a variant this client cannot name.
        builder.addAnnotation(JSON_IGNORE_UNKNOWN_KEYS)
    }

    override fun decorateConstant(builder: PropertySpec.Builder) {
        // Written as a default, and defaults are not encoded unless asked for — without this the
        // discriminator would be missing from everything this client sends.
        builder.addAnnotation(
            AnnotationSpec.builder(ENCODE_DEFAULT).addMember("%T.Mode.ALWAYS", ENCODE_DEFAULT).build(),
        )
    }

    override fun companions(
        model: UnionType,
        baseClass: ClassName,
        subtypeNames: Map<String, ClassName>,
        fallback: ClassName?,
    ): List<TypeSpec> =
        listOf(
            TypeSpec
                .objectBuilder(serializerName(model))
                .addKdoc(selectorKdoc(model))
                .superclass(JSON_CONTENT_POLYMORPHIC_SERIALIZER.parameterizedBy(baseClass))
                .addSuperclassConstructorParameter("%T::class", baseClass)
                .addFunction(
                    FunSpec
                        .builder("selectDeserializer")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("element", JSON_ELEMENT)
                        .returns(DESERIALIZATION_STRATEGY.parameterizedBy(baseClass))
                        .addCode(selector(model, subtypeNames, fallback))
                        .build(),
                ).build(),
        )

    private fun selectorKdoc(model: UnionType) =
        if (model.discriminator != null) {
            "Selects the variant named by `${model.discriminator.wireName}`."
        } else {
            "Selects the variant by the keys present, most specific first — this union declares no discriminator."
        }

    private fun selector(
        model: UnionType,
        subtypeNames: Map<String, ClassName>,
        fallback: ClassName?,
    ): CodeBlock =
        if (model.discriminator != null) {
            byDiscriminator(model, subtypeNames, fallback)
        } else {
            byShape(model, subtypeNames)
        }

    private fun byDiscriminator(
        model: UnionType,
        subtypeNames: Map<String, ClassName>,
        fallback: ClassName?,
    ): CodeBlock =
        CodeBlock
            .builder()
            .beginControlFlow(
                "return when (element.%M[%S]?.%M?.%M)",
                JSON_OBJECT_MEMBER,
                model.discriminator!!.wireName,
                JSON_PRIMITIVE_MEMBER,
                CONTENT_OR_NULL,
            ).apply {
                model.subtypes.forEach { subtype ->
                    addStatement("%S -> %T.serializer()", subtype.wireValue, subtypeNames.getValue(subtype.name))
                }
                addStatement("else -> %T.serializer()", fallback!!)
            }.endControlFlow()
            .build()

    /**
     * Most specific first: a variant whose keys are a superset of another's must be tried before it,
     * or the smaller one always wins and the larger is unreachable.
     */
    private fun byShape(
        model: UnionType,
        subtypeNames: Map<String, ClassName>,
    ): CodeBlock =
        CodeBlock
            .builder()
            .addStatement("val keys = element.%M.keys", JSON_OBJECT_MEMBER)
            .beginControlFlow("return when")
            .apply {
                model.subtypes
                    .sortedByDescending { it.distinguishingKeys.size }
                    .forEach { subtype ->
                        val condition = subtype.distinguishingKeys.joinToString(" || ") { "%S in keys" }
                        addStatement(
                            "$condition -> %T.serializer()",
                            *(subtype.distinguishingKeys.toTypedArray<Any>() + subtypeNames.getValue(subtype.name)),
                        )
                    }
                addStatement(
                    "else -> throw %T(%S + keys)",
                    SERIALIZATION_EXCEPTION,
                    "no ${model.name} variant matches the keys present: ",
                )
            }.endControlFlow()
            .build()

    private fun serializerName(model: UnionType) = "${model.name}Serializer"
}
