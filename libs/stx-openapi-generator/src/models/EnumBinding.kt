package com.strange.openapi.models

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.EnumType

/**
 * What a serialization library needs added to a generated enum so it reads and writes wire values.
 *
 * A generated enum is tolerant, and tolerance cannot be left to the consumer's configuration:
 * Jackson's `@JsonEnumDefaultValue` wants `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` on their
 * `ObjectMapper` and kotlinx's `coerceInputValues` wants it on their `Json`, and this generator
 * owns neither. So each style brings its own mechanism, inside the artifact we ship.
 *
 * The shape being decorated is the same in both styles — same entry names, same `wireValue`, same
 * factory — so a document describes one enum however it is bound.
 */
internal interface EnumBinding {
    /** Adds whatever the style needs on the enum declaration itself. */
    fun decorateEnum(
        builder: TypeSpec.Builder,
        model: EnumType,
    ) = Unit

    /** Adds whatever the style needs on the property holding the wire value. */
    fun decorateWireValue(builder: PropertySpec.Builder) = Unit

    /** Adds whatever the style needs on the factory that maps a wire value back to an entry. */
    fun decorateFactory(builder: FunSpec.Builder) = Unit

    /** Declarations the style needs alongside the enum, in the same file. */
    fun companions(
        model: EnumType,
        enumClass: ClassName,
        wireType: TypeName,
    ): List<TypeSpec> = emptyList()
}

internal val ModelStyle.enumBinding: EnumBinding
    get() =
        when (this) {
            ModelStyle.Kotlinx -> KotlinxEnumBinding
            ModelStyle.Jackson -> JacksonEnumBinding
        }
