package com.softistx.openapi.models

import com.softistx.openapi.EnumType
import com.softistx.openapi.TypeRef
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

/**
 * kotlinx.serialization has no tolerant-enum mode of its own, so the enum gets a serializer.
 *
 * It is a *primitive* serializer, not the plugin-generated enum one: it decodes the wire scalar and
 * falls back when nothing matches, which is exactly what the built-in refuses to do. Going through
 * the wire-value property also means no `@SerialName` is needed on any entry, so an entry name and
 * the value it stands for stay independent.
 */
internal object KotlinxEnumBinding : EnumBinding {
    override fun decorateEnum(
        builder: TypeSpec.Builder,
        model: EnumType,
    ) {
        builder.addAnnotation(
            AnnotationSpec
                .builder(SERIALIZABLE)
                .addMember("with = %N::class", serializerName(model))
                .build(),
        )
    }

    override fun companions(
        model: EnumType,
        enumClass: ClassName,
        wireType: TypeName,
    ): List<TypeSpec> {
        val codec = model.base.codec()
        return listOf(
            TypeSpec
                .objectBuilder(serializerName(model))
                .addKdoc(
                    "Reads a %L, falling back to [%T.%N] for any value this client cannot name.",
                    codec.kind.lowercase(),
                    enumClass,
                    model.fallback.name,
                ).addSuperinterface(K_SERIALIZER.parameterizedBy(enumClass))
                .addProperty(
                    PropertySpec
                        .builder("descriptor", SERIAL_DESCRIPTOR, KModifier.OVERRIDE)
                        .initializer("%M(%S, %T.%L)", PRIMITIVE_DESCRIPTOR, model.name, PRIMITIVE_KIND, codec.kind)
                        .build(),
                ).addFunction(
                    FunSpec
                        .builder("serialize")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("encoder", ENCODER)
                        .addParameter("value", enumClass)
                        .addStatement("encoder.%L(value.%N)", codec.encode, WIRE_VALUE)
                        .build(),
                ).addFunction(
                    FunSpec
                        .builder("deserialize")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("decoder", DECODER)
                        .returns(enumClass)
                        .addStatement("return %T.%N(decoder.%L())", enumClass, FROM_WIRE_VALUE, codec.decode)
                        .build(),
                ).build(),
        )
    }

    private fun serializerName(model: EnumType) = "${model.name}Serializer"
}

/** How the wire scalar is described, encoded and decoded. */
private data class Codec(
    val kind: String,
    val encode: String,
    val decode: String,
)

private fun TypeRef.codec(): Codec =
    when (this) {
        TypeRef.IntRef -> Codec("INT", "encodeInt", "decodeInt")
        TypeRef.LongRef -> Codec("LONG", "encodeLong", "decodeLong")
        else -> Codec("STRING", "encodeString", "decodeString")
    }
