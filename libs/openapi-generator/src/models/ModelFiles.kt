package com.strange.openapi.models

import com.squareup.kotlinpoet.FileSpec
import com.strange.openapi.ApiModel
import com.strange.openapi.EnumType
import com.strange.openapi.ModelType
import com.strange.openapi.ObjectType
import com.strange.openapi.UnionType
import com.strange.openapi.emit.EmitOptions

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
        }
    }
