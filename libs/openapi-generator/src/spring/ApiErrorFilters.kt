package com.strange.openapi.spring

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
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

private const val ATTRIBUTE = "OPERATION_ATTRIBUTE"

/**
 * `ApiErrors.kt`: the two pieces of Spring wiring that turn a documented failure into its exception.
 *
 * Spring's proxy sees the method; its `WebClient` sees the response; nothing sees both. So the
 * operation travels between them as a request attribute:
 *
 * - `apiOperationProcessor()` is an `HttpRequestValues.Processor`, which the proxy factory hands
 *   the reflective `Method` — enough to read `@ApiOperation` off the generated interface — and a
 *   builder with `addAttribute`.
 * - `WebClientAdapter` copies those attributes onto the `ClientRequest`, where `apiErrorFilter()`
 *   reads them back beside the response.
 *
 * Both halves were checked against the real stack before this was written, not inferred from the
 * API surface.
 *
 * Generated because the mapping follows the document; installed by the consumer, because the
 * consumer owns the `WebClient` and the factory — the same split as `ApiEnumConverters.kt`:
 *
 * ```kotlin
 * val webClient = WebClient.builder().baseUrl(url).filter(apiErrorFilter(mapper)).build()
 * HttpServiceProxyFactory
 *     .builderFor(WebClientAdapter.create(webClient))
 *     .httpRequestValuesProcessor(apiOperationProcessor())
 *     .build()
 * ```
 */
internal fun apiErrorFilterFile(
    model: ApiModel,
    options: EmitOptions,
): FileSpec? {
    val schemas = model.errorSchemas()
    if (schemas.isEmpty()) return null

    val attribute =
        PropertySpec
            .builder(ATTRIBUTE, STRING)
            .addModifiers(KModifier.PUBLIC, KModifier.CONST)
            .initializer("%S", "${options.packageName}.operationId")
            .addKdoc(
                """
                The request attribute the operation's id travels in.

                From the proxy, which knows the method but not the response, to the filter, which
                sees the response but not the method.
                """.trimIndent(),
            ).build()

    val processor =
        FunSpec
            .builder("apiOperationProcessor")
            .addModifiers(KModifier.PUBLIC)
            .returns(PROCESSOR)
            .addKdoc(
                """
                Puts the calling operation's id where [apiErrorFilter] can find it.

                Give it to the proxy factory with `httpRequestValuesProcessor(...)`. Without it the
                filter has a status and a body but no way to know which operation they came from,
                and every failure falls back to [$API_EXCEPTION].
                """.trimIndent(),
            ).addCode(
                CodeBlock
                    .builder()
                    .beginControlFlow("return %T { method, _, _, builder ->", PROCESSOR)
                    .addStatement(
                        "method.getAnnotation(%T::class.java)?.let { builder.addAttribute(%L, it.id) }",
                        apiOperationName(options),
                        ATTRIBUTE,
                    ).endControlFlow()
                    .build(),
            ).build()

    val filter =
        FunSpec
            .builder("apiErrorFilter")
            .addModifiers(KModifier.PUBLIC)
            .addParameter(
                ParameterSpec
                    .builder("mapper", OBJECT_MAPPER)
                    // findAndAddModules(), not a bare builder: the generated models are Kotlin
                    // data classes, and without jackson-module-kotlin on the mapper Jackson binds
                    // them to an object whose every property is null — a parsed error body that
                    // silently says nothing. Caught by the round-trip below, not by the compiler.
                    .defaultValue("%T.builder().findAndAddModules().build()", JSON_MAPPER)
                    .build(),
            ).returns(EXCHANGE_FILTER)
            .addKdoc(
                """
                Throws the exception the document describes for a non-2xx response.

                Add it to the `WebClient` the proxy is built on. Without it Spring reports a failed
                call as a `WebClientResponseException` carrying an unparsed body, and the document's
                own account of the failure goes unread.

                The default mapper discovers the modules on the classpath, which is what lets it
                bind the generated Kotlin models. Pass the `WebClient`'s own mapper instead if it
                is configured differently.
                """.trimIndent(),
            ).addCode(
                CodeBlock
                    .builder()
                    .beginControlFlow("return %T { request, next ->", EXCHANGE_FILTER)
                    .beginControlFlow("next.exchange(request).flatMap { response ->")
                    .beginControlFlow("if (response.statusCode().isError)")
                    .beginControlFlow(
                        "response.bodyToMono(%T::class.java).defaultIfEmpty(%S).flatMap { rawBody ->",
                        STRING,
                        "",
                    ).addStatement("val operationId = request.attributes()[%L] as %T?", ATTRIBUTE, STRING)
                    .addStatement(
                        "%T.error<%T>(%L(mapper, operationId, response.statusCode().value(), rawBody))",
                        MONO,
                        CLIENT_RESPONSE,
                        API_ERROR_OF,
                    ).endControlFlow()
                    .nextControlFlow("else")
                    .addStatement("%T.just(response)", MONO)
                    .endControlFlow()
                    .endControlFlow()
                    .endControlFlow()
                    .build(),
            ).build()

    val decoder = listOf(ParameterSpec.builder("mapper", OBJECT_MAPPER).build())
    val parsers =
        schemas.map { schema ->
            FunSpec
                .builder(parserNameFor(schema))
                .addModifiers(KModifier.PRIVATE)
                .addParameter("mapper", OBJECT_MAPPER)
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
                            "%T(status, rawBody, mapper.readValue(rawBody, %T::class.java))",
                            ClassName(options.packageName, exceptionNameFor(schema)),
                            ClassName(options.modelPackage, schema),
                        ).nextControlFlow("catch (e: %T)", EXCEPTION)
                        .addStatement("%T(status, rawBody)", apiExceptionName(options))
                        .endControlFlow()
                        .build(),
                ).build()
        }

    return FileSpec
        .builder(options.packageName, "ApiErrors")
        .addFileComment(GENERATED_COMMENT)
        .addProperty(attribute)
        .addFunction(processor)
        .addFunction(filter)
        .addFunction(apiErrorDispatch(model, options, decoder, "mapper, status, rawBody"))
        .apply { parsers.forEach { addFunction(it) } }
        .build()
}

// Jackson 3 lives under tools.jackson; only its annotations stayed at com.fasterxml.
private val OBJECT_MAPPER = ClassName("tools.jackson.databind", "ObjectMapper")
private val JSON_MAPPER = ClassName("tools.jackson.databind.json", "JsonMapper")
private val EXCHANGE_FILTER = ClassName("org.springframework.web.reactive.function.client", "ExchangeFilterFunction")
private val CLIENT_RESPONSE = ClassName("org.springframework.web.reactive.function.client", "ClientResponse")
private val PROCESSOR = ClassName("org.springframework.web.service.invoker", "HttpRequestValues", "Processor")
private val MONO = ClassName("reactor.core.publisher", "Mono")

// The broadest catch on purpose: Jackson throws for a body of the wrong shape and for one that is
// not JSON at all, and the error path must not fail while reporting a failure.
private val EXCEPTION = ClassName("kotlin", "Exception")
