package com.strange.openapi.models

import com.squareup.kotlinpoet.FileSpec
import com.strange.openapi.ApiModel
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.SourceEmitter
import com.strange.openapi.emit.endpointsFile

/**
 * Emits the spec's schemas and its endpoint constants — the `client: None` case.
 *
 * Useful where the API surface is written by hand or comes from elsewhere, but the payload types
 * should still follow the document: a server implementing the spec, or a module that only needs
 * to deserialize responses it receives.
 *
 * **No API surface, but the routes still come from the document.** This emitter deliberately emits
 * no interface and no exception hierarchy — an exception nothing can throw is API surface. `Endpoints`
 * is not: it is the document's own path strings, and this is the client kind that needs them most.
 * A hand-written server is the only one with no generated interface to drift against, so its routes
 * are the routes that go stale in silence.
 */
public class ModelsOnlyEmitter(
    private val style: ModelStyle = ModelStyle.Kotlinx,
) : SourceEmitter {
    override fun emit(
        model: ApiModel,
        options: EmitOptions,
    ): List<FileSpec> = modelFiles(model, options, style) + endpointsFile(model, options)
}
