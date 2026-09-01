package com.softistx.openapi.models

import com.softistx.openapi.ApiModel
import com.softistx.openapi.EnumType
import com.softistx.openapi.ModelType
import com.softistx.openapi.ObjectType
import com.softistx.openapi.UnionType
import com.softistx.openapi.ValueClassType
import com.softistx.openapi.emit.EmitOptions
import com.squareup.kotlinpoet.FileSpec

/**
 * One file per model declaration, in the style the caller's serializer needs.
 *
 * Every emitter goes through here, so a Ktorfit client and a Spring client describe the same
 * document with the same names — only the annotations differ.
 *
 * The dispatch is exhaustive over [ModelType] on purpose: a new kind of declaration cannot be
 * added to the IR without deciding, here, how it is written.
 */
internal fun modelFiles(
    model: ApiModel,
    options: EmitOptions,
    style: ModelStyle,
): List<FileSpec> =
    model.models.map { declaration ->
        when (declaration) {
            is ObjectType -> objectFile(declaration, options, style)
            is EnumType -> enumFile(declaration, options, style)
            is UnionType -> unionFile(declaration, options, style)
            is ValueClassType -> valueClassFile(declaration, options, style)
        }
    }
