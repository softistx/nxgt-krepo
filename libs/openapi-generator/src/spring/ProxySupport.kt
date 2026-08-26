package com.strange.openapi.spring

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.GENERATED_COMMENT
import com.strange.openapi.emit.apiOperationName

internal const val OPERATION_ATTRIBUTE: String = "OPERATION_ATTRIBUTE"

internal const val SECURITY_ATTRIBUTE: String = "SECURITY_ATTRIBUTE"

/**
 * `ApiProxySupport.kt`: how a generated Spring client gets the operation down to the HTTP layer.
 *
 * Spring's proxy sees the method; its `WebClient` sees the request and the response; nothing sees
 * both. The proxy factory does hand an `HttpRequestValues.Processor` the reflective `Method`,
 * though, and `WebClientAdapter` copies the attributes it sets onto the `ClientRequest` — so the
 * operation travels between the two as a pair of request attributes. Checked against the running
 * stack before this was written, not inferred from the API surface.
 *
 * Ktorfit needs none of this: it puts a function's own annotations on the request, and a plugin
 * reads them there.
 */
internal fun proxySupportFile(options: EmitOptions): FileSpec {
    val operation =
        PropertySpec
            .builder(OPERATION_ATTRIBUTE, STRING)
            .addModifiers(KModifier.PUBLIC, KModifier.CONST)
            .initializer("%S", "${options.packageName}.operationId")
            .addKdoc("The request attribute the calling operation's id travels in.")
            .build()

    val security =
        PropertySpec
            .builder(SECURITY_ATTRIBUTE, STRING)
            .addModifiers(KModifier.PUBLIC, KModifier.CONST)
            .initializer("%S", "${options.packageName}.security")
            .addKdoc("The request attribute the calling operation's `security` requirement travels in.")
            .build()

    val processor =
        FunSpec
            .builder("apiOperationProcessor")
            .addModifiers(KModifier.PUBLIC)
            .returns(PROCESSOR)
            .addKdoc(
                """
                Puts the calling operation where the generated filters can find it.

                Give it to the proxy factory with `httpRequestValuesProcessor(...)`. Without it a
                filter has a request and a response but no idea which operation they belong to: the
                error filter falls back to the untyped exception for everything, and the auth filter
                attaches nothing.
                """.trimIndent(),
            ).addCode(
                CodeBlock
                    .builder()
                    .beginControlFlow("return %T { method, _, _, builder ->", PROCESSOR)
                    .beginControlFlow("method.getAnnotation(%T::class.java)?.let", apiOperationName(options))
                    .addStatement("builder.addAttribute(%L, it.id)", OPERATION_ATTRIBUTE)
                    .addStatement("builder.addAttribute(%L, it.security.toList())", SECURITY_ATTRIBUTE)
                    .endControlFlow()
                    .endControlFlow()
                    .build(),
            ).build()

    return FileSpec
        .builder(options.packageName, "ApiProxySupport")
        .addFileComment(GENERATED_COMMENT)
        .addProperty(operation)
        .addProperty(security)
        .addFunction(processor)
        .build()
}

private val PROCESSOR = ClassName("org.springframework.web.service.invoker", "HttpRequestValues", "Processor")
