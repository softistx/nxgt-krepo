package com.strange.openapi.models

import com.squareup.kotlinpoet.FileSpec
import com.strange.openapi.ApiModel
import com.strange.openapi.EmitOptions
import com.strange.openapi.SourceEmitter

/**
 * Emits the spec's schemas and nothing else — the `client: None` case.
 *
 * Useful where the API surface is written by hand or comes from elsewhere, but the payload types
 * should still follow the document: a server implementing the spec, or a module that only needs
 * to deserialize responses it receives.
 */
public class ModelsOnlyEmitter(
    private val style: ModelStyle = ModelStyle.Kotlinx,
) : SourceEmitter {
    override fun emit(
        model: ApiModel,
        options: EmitOptions,
    ): List<FileSpec> = modelFiles(model, options, style)
}
