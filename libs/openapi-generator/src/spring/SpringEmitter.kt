package com.strange.openapi.spring

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
import com.strange.openapi.emit.SourceEmitter
import com.strange.openapi.emit.apiFile
import com.strange.openapi.emit.optionality
import com.strange.openapi.emit.typeNameOf
import com.strange.openapi.models.ModelStyle
import com.strange.openapi.models.modelFiles
import com.strange.openapi.models.types

/**
 * Emits Spring [HTTP interfaces](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface):
 * `@HttpExchange` interfaces that `HttpServiceProxyFactory` turns into a client at runtime.
 *
 * Unlike the Ktorfit output there is no annotation processing step — the consuming module needs
 * `spring-web` and builds the proxy itself:
 *
 * ```kotlin
 * val factory = HttpServiceProxyFactory
 *     .builderFor(WebClientAdapter.create(WebClient.create("http://localhost:8080")))
 *     .build()
 * val categories = factory.createClient(CategoriesApi::class.java)
 * ```
 *
 * The functions are `suspend`, which Spring supports through a reactive adapter — `WebClientAdapter`,
 * not `RestClientAdapter` — and which needs `kotlinx-coroutines-reactor` on the classpath.
 *
 * Models are plain data classes: Jackson binds them without annotations, so nothing is emitted
 * unless a wire name differs from its Kotlin name.
 */
public class SpringEmitter(
    /**
     * Spring binds with Jackson by default, but a `WebClient` configured with
     * `KotlinSerializationJsonEncoder` wants kotlinx-serializable models instead.
     */
    private val style: ModelStyle = ModelStyle.Jackson,
) : SourceEmitter {
    override fun emit(
        model: ApiModel,
        options: EmitOptions,
    ): List<FileSpec> = model.groups.map { emitGroup(it, options) } + modelFiles(model, options, style)

    private fun emitGroup(
        group: ApiGroup,
        options: EmitOptions,
    ): FileSpec =
        apiFile(group, options, annotations = listOf(AnnotationSpec.builder(HTTP_EXCHANGE).build())) {
            emitOperation(it, options)
        }

    private fun emitOperation(
        operation: Operation,
        options: EmitOptions,
    ): FunSpec {
        val exchange =
            exchangeAnnotation(operation)
                .addMember("url = %S", operation.path)
                .apply { contentTypeOf(operation)?.let { addMember("contentType = %S", it) } }
                .build()

        val builder =
            FunSpec
                .builder(operation.name)
                .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT, KModifier.SUSPEND)
                .addAnnotation(exchange)
                .returns(typeNameOf(operation.returnType, options, style.types))

        operation.summary?.takeIf { it.isNotBlank() }?.let { builder.addKdoc("%L", it) }
        // Parameters that get a `= null` default must come last, or callers could not omit them.
        operation.parameters.sortedBy { it.optionality.defaultSource != null }.forEach { builder.addParameter(emitParam(it, options)) }
        return builder.build()
    }

    /**
     * Spring picks a content type from the configured message converters, but only when the
     * request carries a body; being explicit keeps the generated client from depending on how
     * the caller happened to configure its `WebClient`.
     */
    private fun contentTypeOf(operation: Operation): String? =
        when {
            operation.parameters.any { it.kind == ParamKind.Part } -> "multipart/form-data"
            operation.parameters.any { it.kind == ParamKind.Body } -> "application/json"
            else -> null
        }

    private fun emitParam(
        param: Param,
        options: EmitOptions,
    ): ParameterSpec {
        // Spring's argument resolvers reject a null value for a required named parameter, so an
        // optional one has to say required = false as well as being nullable.
        val annotation =
            when (param.kind) {
                ParamKind.Path -> named(PATH_VARIABLE, param)
                ParamKind.Query -> named(REQUEST_PARAM, param)
                ParamKind.Header -> named(REQUEST_HEADER, param)
                ParamKind.Part -> named(REQUEST_PART, param)
                ParamKind.Body -> AnnotationSpec.builder(REQUEST_BODY).build()
            }
        val optionality = param.optionality
        val type = typeNameOf(param.type, options, style.types).copy(nullable = optionality.nullable)
        return ParameterSpec
            .builder(param.name, type)
            .addAnnotation(annotation)
            .apply { optionality.defaultSource?.let { defaultValue(it) } }
            .build()
    }

    private fun named(
        annotation: ClassName,
        param: Param,
    ): AnnotationSpec =
        AnnotationSpec
            .builder(annotation)
            .addMember("name = %S", param.wireName)
            .apply { if (!param.required) addMember("required = false") }
            .build()

    /**
     * `@GetExchange` and friends cover the five verbs Spring gives a shortcut for; the rest go
     * through the generic `@HttpExchange(method = ...)`, which is the same annotation the
     * shortcuts are themselves meta-annotated with.
     */
    private fun exchangeAnnotation(operation: Operation): AnnotationSpec.Builder =
        when (val method = operation.httpMethod.uppercase()) {
            "GET" -> AnnotationSpec.builder(exchange("GetExchange"))
            "POST" -> AnnotationSpec.builder(exchange("PostExchange"))
            "PUT" -> AnnotationSpec.builder(exchange("PutExchange"))
            "PATCH" -> AnnotationSpec.builder(exchange("PatchExchange"))
            "DELETE" -> AnnotationSpec.builder(exchange("DeleteExchange"))
            in GENERIC_METHODS -> AnnotationSpec.builder(HTTP_EXCHANGE).addMember("method = %S", method)
            else -> throw EmitException("Spring cannot express HTTP method '$method'")
        }

    private fun exchange(simpleName: String) = ClassName(SERVICE_ANNOTATION, simpleName)

    private companion object {
        const val SERVICE_ANNOTATION = "org.springframework.web.service.annotation"
        const val BIND_ANNOTATION = "org.springframework.web.bind.annotation"
        val HTTP_EXCHANGE = ClassName(SERVICE_ANNOTATION, "HttpExchange")
        val PATH_VARIABLE = ClassName(BIND_ANNOTATION, "PathVariable")
        val REQUEST_PARAM = ClassName(BIND_ANNOTATION, "RequestParam")
        val REQUEST_HEADER = ClassName(BIND_ANNOTATION, "RequestHeader")
        val REQUEST_BODY = ClassName(BIND_ANNOTATION, "RequestBody")
        val REQUEST_PART = ClassName(BIND_ANNOTATION, "RequestPart")
        val GENERIC_METHODS = setOf("HEAD", "OPTIONS", "TRACE")
    }
}
