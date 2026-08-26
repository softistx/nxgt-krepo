package com.strange.openapi.ktorfit

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.strange.openapi.ApiGroup
import com.strange.openapi.ApiModel
import com.strange.openapi.Operation
import com.strange.openapi.Param
import com.strange.openapi.ParamKind
import com.strange.openapi.emit.EmitException
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.Optionality
import com.strange.openapi.emit.SourceEmitter
import com.strange.openapi.emit.apiFile
import com.strange.openapi.emit.optionalityOf
import com.strange.openapi.emit.typeNameOf
import com.strange.openapi.models.ModelStyle
import com.strange.openapi.models.modelFiles
import com.strange.openapi.models.types

/**
 * Emits [Ktorfit](https://foso.github.io/Ktorfit/) interfaces plus `@Serializable` models.
 *
 * The interfaces are only half the client: `ktorfit-ksp` reads them and generates the
 * `createXxxApi()` builders that actually construct one. The consuming module therefore needs
 * `ktorfit-lib` plus `ktorfit-ksp` under `settings.kotlin.ksp.processors`, and
 * `settings.kotlin.serialization: json`.
 */
public class KtorfitEmitter : SourceEmitter {
    override fun emit(
        model: ApiModel,
        options: EmitOptions,
    ): List<FileSpec> = model.groups.map { emitGroup(it, options) } + modelFiles(model, options, STYLE)

    private fun emitGroup(
        group: ApiGroup,
        options: EmitOptions,
    ): FileSpec = apiFile(group, options) { emitOperation(it, options) }

    private fun emitOperation(
        operation: Operation,
        options: EmitOptions,
    ): FunSpec {
        val builder =
            FunSpec
                .builder(operation.name)
                .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT, KModifier.SUSPEND)
                .addAnnotation(
                    AnnotationSpec
                        .builder(methodAnnotation(operation.httpMethod))
                        .addMember("%S", operation.path)
                        .build(),
                ).returns(typeNameOf(operation.returnType, options, STYLE.types))

        operation.summary?.takeIf { it.isNotBlank() }?.let { builder.addKdoc("%L", it) }
        // The signature cannot show that the spec called these parts optional, so the KDoc does.
        operation.parameters
            .filter { it.kind == ParamKind.Part && !it.required }
            .forEach {
                builder.addKdoc(
                    "\n\n@param %L optional in the OpenAPI document, but Ktorfit does not allow a nullable @Part.",
                    it.name,
                )
            }
        if (operation.parameters.any { it.kind == ParamKind.Part }) {
            builder.addAnnotation(ktorfit("Multipart"))
        }
        if (operation.parameters.any { it.kind == ParamKind.Body }) {
            // Ktor refuses to serialize a body it has no Content-Type for ("Fail to prepare
            // request body for sending ... with Content-Type: null"), and the parser only
            // produces a @Body param for an application/json request body.
            builder.addAnnotation(
                AnnotationSpec
                    .builder(ktorfit("Headers"))
                    .addMember("%S", "Content-Type: application/json")
                    .build(),
            )
        }
        // Parameters that get a `= null` default must come last, or callers could not omit them.
        operation.parameters.sortedBy { it.partSafeOptionality.defaultSource != null }.forEach {
            builder.addParameter(
                emitParam(it, options),
            )
        }
        return builder.build()
    }

    private fun emitParam(
        param: Param,
        options: EmitOptions,
    ): ParameterSpec {
        val annotation =
            when (param.kind) {
                ParamKind.Path -> AnnotationSpec.builder(ktorfit("Path")).addMember("%S", param.wireName).build()
                ParamKind.Query -> AnnotationSpec.builder(ktorfit("Query")).addMember("%S", param.wireName).build()
                ParamKind.Header -> AnnotationSpec.builder(ktorfit("Header")).addMember("%S", param.wireName).build()
                ParamKind.Part -> AnnotationSpec.builder(ktorfit("Part")).addMember("%S", param.wireName).build()
                ParamKind.Body -> AnnotationSpec.builder(ktorfit("Body")).build()
            }
        val optionality = param.partSafeOptionality
        val type = typeNameOf(param.type, options, STYLE.types).copy(nullable = optionality.nullable)
        return ParameterSpec
            .builder(param.name, type)
            .addAnnotation(annotation)
            .apply { optionality.defaultSource?.let { defaultValue(it) } }
            .build()
    }

    /**
     * How the parameter is written, with one Ktorfit-specific override.
     *
     * Ktorfit's KSP processor rejects a nullable `@Part` ("Part parameter type may not be
     * nullable"), so multipart parts stay non-null and undefaulted even when the spec makes them
     * optional. Everything else follows the shared rule, so a parameter and the model property it
     * carries agree about nullability.
     */
    private val Param.partSafeOptionality: Optionality
        get() =
            optionalityOf(type, required, nullable, default)
                .takeIf { kind != ParamKind.Part }
                ?: Optionality(nullable = false, defaultSource = null)

    private fun ktorfit(simpleName: String) = ClassName(KTORFIT_HTTP, simpleName)

    private fun methodAnnotation(method: String) =
        when (method.uppercase()) {
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" -> ktorfit(method.uppercase())
            else -> throw EmitException("Ktorfit has no annotation for HTTP method '$method'")
        }

    private companion object {
        const val KTORFIT_HTTP = "de.jensklingenberg.ktorfit.http"

        // Not a constructor parameter, unlike SpringEmitter's: Ktorfit deserializes through
        // Ktor's ContentNegotiation, which this repo configures with kotlinx.serialization.
        val STYLE = ModelStyle.Kotlinx
    }
}
