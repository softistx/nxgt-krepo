package com.softistx.openapi.models

import com.softistx.openapi.UnionType
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeSpec

/**
 * Jackson is told the shape of the hierarchy and does the selecting itself.
 *
 * Two of the flags below are not the obvious ones, and both exist for the same reason: the document
 * declares the discriminator as a property, so the class already has it. `As.EXISTING_PROPERTY`
 * stops Jackson writing a second copy, and `visible = true` is what makes it hand the value back to
 * the constructor instead of consuming it — without which the Kotlin property has nothing to bind.
 */
internal object JacksonUnionBinding : UnionBinding {
    override fun decorateBase(
        builder: TypeSpec.Builder,
        model: UnionType,
        subtypeNames: Map<String, ClassName>,
        fallback: ClassName?,
    ) {
        builder.addAnnotation(typeInfo(model, fallback))
        builder.addAnnotation(subTypes(model, subtypeNames))
    }

    override fun decorateFallback(builder: TypeSpec.Builder) {
        // The keys of a variant this client cannot name; a consumer with FAIL_ON_UNKNOWN_PROPERTIES
        // on would otherwise reject the whole payload.
        builder.addAnnotation(
            AnnotationSpec.builder(JSON_IGNORE_PROPERTIES).addMember("ignoreUnknown = true").build(),
        )
    }

    private fun typeInfo(
        model: UnionType,
        fallback: ClassName?,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(JSON_TYPE_INFO)
            .apply {
                if (model.discriminator == null) {
                    addMember("use = %T.Id.DEDUCTION", JSON_TYPE_INFO)
                } else {
                    addMember("use = %T.Id.NAME", JSON_TYPE_INFO)
                    addMember("include = %T.As.EXISTING_PROPERTY", JSON_TYPE_INFO)
                    addMember("property = %S", model.discriminator.wireName)
                    addMember("visible = true")
                    fallback?.let { addMember("defaultImpl = %T::class", it) }
                }
            }.build()

    private fun subTypes(
        model: UnionType,
        subtypeNames: Map<String, ClassName>,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(JSON_SUB_TYPES)
            .apply {
                model.subtypes.forEach { subtype ->
                    val type = subtypeNames.getValue(subtype.name)
                    addMember(
                        if (subtype.wireValue == null) {
                            CodeBlock.of("%T.Type(value = %T::class)", JSON_SUB_TYPES, type)
                        } else {
                            CodeBlock.of("%T.Type(value = %T::class, name = %S)", JSON_SUB_TYPES, type, subtype.wireValue)
                        },
                    )
                }
            }.build()
}
