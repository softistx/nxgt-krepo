package com.strange.openapi.ktorfit

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.ApiModel
import com.strange.openapi.emit.API_ERROR_OF
import com.strange.openapi.emit.API_EXCEPTION
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.GENERATED_COMMENT
import com.strange.openapi.emit.apiErrorDispatch
import com.strange.openapi.emit.apiExceptionName
import com.strange.openapi.emit.apiOperationName
import com.strange.openapi.emit.errorSchemas
import com.strange.openapi.emit.exceptionNameFor
import com.strange.openapi.emit.parserNameFor

private const val CONFIG = "ApiErrorsConfig"

/**
 * `ApiErrors.kt`: the Ktor plugin that turns a documented failure into the exception for it.
 *
 * Ktor leaves `expectSuccess` off, so without this a `404` carrying an error body is handed to the
 * caller's deserializer as if it were the success type, and what surfaces is whatever the
 * deserializer says about a body of the wrong shape. The status never reaches the caller and the
 * parsed body never exists.
 *
 * `on(Send)` rather than `HttpResponseValidator`: it is the one hook that can see both the
 * function's own annotations — Ktorfit puts them on the request, and they survive its KSP
 * processor onto the generated implementation — and the response, before anything tries to read
 * the body as the success type. Verified end to end against the demo server rather than assumed.
 *
 * The consumer installs it, because the consumer owns the `HttpClient`:
 *
 * ```kotlin
 * HttpClient(CIO) {
 *     install(ContentNegotiation) { json() }
 *     install(ApiErrors)
 * }
 * ```
 */
internal fun apiErrorsFile(
    model: ApiModel,
    options: EmitOptions,
): FileSpec? {
    val schemas = model.errorSchemas()
    if (schemas.isEmpty()) return null

    val config =
        TypeSpec
            .classBuilder(CONFIG)
            .addModifiers(KModifier.PUBLIC)
            .addKdoc("Configuration for the [ApiErrors] plugin.")
            .addProperty(
                PropertySpec
                    .builder("json", JSON)
                    .mutable()
                    .initializer("%T { ignoreUnknownKeys = true }", JSON)
                    .addKdoc(
                        """
                        Parses an error body.

                        Lenient by default, and its own instance rather than the one
                        `ContentNegotiation` was given: an error body is the last thing that should
                        fail for carrying a field this client has not heard of.
                        """.trimIndent(),
                    ).build(),
            ).build()

    val plugin =
        PropertySpec
            .builder("ApiErrors", CLIENT_PLUGIN.parameterizedBy(ClassName(options.utilPackage, CONFIG)))
            .addModifiers(KModifier.PUBLIC)
            .addKdoc(
                """
                Throws the exception the document describes for a non-2xx response.

                Install it on the `HttpClient` the `Ktorfit` instance is built with; without it a
                failed call surfaces as a deserialization error about the success type.
                """.trimIndent(),
            ).initializer(
                buildString {
                    appendLine("%M(%S, ::%L) {")
                    appendLine("    val json = pluginConfig.json")
                    appendLine("    on(%M) { request ->")
                    appendLine("        val call = proceed(request)")
                    appendLine("        if (call.response.status.%M()) return@on call")
                    appendLine("        val operationId = request.%M.filterIsInstance<%T>().firstOrNull()?.id")
                    appendLine("        throw %L(json, operationId, call.response.status.value, call.response.%M())")
                    appendLine("    }")
                    append("}")
                },
                CREATE_CLIENT_PLUGIN,
                "ApiErrors",
                CONFIG,
                SEND,
                IS_SUCCESS,
                ANNOTATIONS,
                apiOperationName(options),
                com.strange.openapi.emit.API_ERROR_OF,
                BODY_AS_TEXT,
            ).build()

    val decoder = listOf(ParameterSpec.builder("json", JSON).build())
    val parsers =
        schemas.map { schema ->
            FunSpec
                .builder(parserNameFor(schema))
                .addModifiers(KModifier.PRIVATE)
                .addParameter("json", JSON)
                .addParameter("status", INT)
                .addParameter("rawBody", STRING)
                .returns(apiExceptionName(options))
                .addKdoc(
                    """
                    A `%L` body, if it is one.

                    Any failure to parse falls back to [%L]: the error path is reporting something
                    that already went wrong, and throwing its own exception on top would hide it.
                    """.trimIndent(),
                    schema,
                    API_EXCEPTION,
                ).addCode(
                    CodeBlock
                        .builder()
                        .beginControlFlow("return try")
                        .addStatement(
                            "%T(status, rawBody, json.decodeFromString(%T.serializer(), rawBody))",
                            ClassName(options.utilPackage, exceptionNameFor(schema)),
                            ClassName(options.modelPackage, schema),
                        ).nextControlFlow("catch (e: %T)", EXCEPTION)
                        .addStatement("%T(status, rawBody)", apiExceptionName(options))
                        .endControlFlow()
                        .build(),
                ).build()
        }

    return FileSpec
        .builder(options.utilPackage, "ApiErrors")
        .addFileComment(GENERATED_COMMENT)
        .addType(config)
        .addProperty(plugin)
        .addFunction(apiErrorDispatch(model, options, decoder, "json, status, rawBody"))
        .apply { parsers.forEach { addFunction(it) } }
        .build()
}

// Deliberately the broadest catch: kotlinx throws SerializationException for a body of the wrong
// shape and IllegalArgumentException for one that is not JSON at all, and a third kind would be a
// surprise this generator should absorb rather than pass on.
private val EXCEPTION = ClassName("kotlin", "Exception")
private val JSON = ClassName("kotlinx.serialization.json", "Json")
private val CLIENT_PLUGIN = ClassName("io.ktor.client.plugins.api", "ClientPlugin")
private val CREATE_CLIENT_PLUGIN = MemberName("io.ktor.client.plugins.api", "createClientPlugin")
private val SEND = MemberName("io.ktor.client.plugins.api", "Send")
private val IS_SUCCESS = MemberName("io.ktor.http", "isSuccess")
private val ANNOTATIONS = MemberName("de.jensklingenberg.ktorfit", "annotations")
private val BODY_AS_TEXT = MemberName("io.ktor.client.statement", "bodyAsText")
