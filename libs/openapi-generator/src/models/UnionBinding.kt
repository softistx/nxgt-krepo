package com.strange.openapi.models

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.UnionType

/**
 * What a serialization library needs so a sealed hierarchy round-trips.
 *
 * The two libraries reach the same place by opposite routes. Jackson describes the hierarchy in
 * annotations and does the selecting itself; kotlinx has to be handed a serializer that selects,
 * because its built-in polymorphism insists on writing a discriminator of its own — which would
 * appear twice next to a discriminator the document already declares as a property.
 */
internal interface UnionBinding {
    /** Adds whatever the style needs on the sealed interface. */
    fun decorateBase(
        builder: TypeSpec.Builder,
        model: UnionType,
        subtypeNames: Map<String, ClassName>,
        fallback: ClassName?,
    )

    /** Adds whatever the style needs on the generated catch-all subtype. */
    fun decorateFallback(builder: TypeSpec.Builder) = Unit

    /** Adds whatever the style needs on a discriminator property that always holds one value. */
    fun decorateConstant(builder: PropertySpec.Builder) = Unit

    /** Declarations the style needs alongside the base, in the same file. */
    fun companions(
        model: UnionType,
        baseClass: ClassName,
        subtypeNames: Map<String, ClassName>,
        fallback: ClassName?,
    ): List<TypeSpec> = emptyList()

    /** Anything the file itself needs — an opt-in, typically. */
    fun decorateFile(builder: FileSpec.Builder) = Unit
}

internal val ModelStyle.unionBinding: UnionBinding
    get() =
        when (this) {
            ModelStyle.Kotlinx -> KotlinxUnionBinding
            ModelStyle.Jackson -> JacksonUnionBinding
        }
