package com.strange.openapi.ktorfit

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.strange.openapi.ApiModel
import com.strange.openapi.SecurityKind
import com.strange.openapi.SecurityScheme
import com.strange.openapi.emit.AUTH_CONFIG
import com.strange.openapi.emit.EmitOptions
import com.strange.openapi.emit.GENERATED_COMMENT
import com.strange.openapi.emit.apiOperationName
import com.strange.openapi.emit.authConfigType
import com.strange.openapi.emit.basicCredentialsType
import com.strange.openapi.emit.credentialSchemes
import com.strange.openapi.emit.needsBasicCredentials

/**
 * `ApiAuth.kt`: the Ktor plugin that attaches the credential each operation asks for.
 *
 * The document knows which operations need which scheme and which are open — this one declares
 * `Bearer` at its root and overrides it with `security: []` on the operations that hand out the
 * token in the first place — and until now none of that reached the client. A hand-written
 * interceptor either sends the header everywhere, including to the sign-in endpoint, or keeps its
 * own copy of the list.
 *
 * `onRequest` reads the requirement off `@ApiOperation`, which Ktorfit puts on the request and
 * ktorfit-ksp carries through from the generated interface.
 *
 * ```kotlin
 * install(ApiAuth) { bearer = { tokenStore.current() } }
 * ```
 */
internal fun apiAuthFile(
    model: ApiModel,
    options: EmitOptions,
): FileSpec? {
    val schemes = model.credentialSchemes()
    if (schemes.isEmpty()) return null

    val body =
        CodeBlock
            .builder()
            .beginControlFlow("%M(%S, ::%L) {", CREATE_CLIENT_PLUGIN, "ApiAuth", AUTH_CONFIG)
            .addStatement("val credentials = pluginConfig")
            .beginControlFlow("onRequest { request, _ ->")
            .addStatement(
                "val required = request.%M.filterIsInstance<%T>().firstOrNull()?.security ?: return@onRequest",
                ANNOTATIONS,
                apiOperationName(options),
            ).beginControlFlow("required.forEach { scheme ->")
            .beginControlFlow("when (scheme)")
            .apply { schemes.forEach { add(it.attach()) } }
            // A requirement naming a scheme with no slot cannot happen — the parser rejects a
            // requirement the document does not declare, and the emitter rejects one no client can
            // satisfy — but `when` over a String is not exhaustive, so it has to be said.
            .addStatement("else -> Unit")
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
            .endControlFlow()
            .build()

    val plugin =
        PropertySpec
            .builder("ApiAuth", CLIENT_PLUGIN.parameterizedBy(ClassName(options.utilPackage, AUTH_CONFIG)))
            .addModifiers(KModifier.PUBLIC)
            .addKdoc(
                """
                Attaches the credential each operation's `security` asks for, and nothing to the
                operations that ask for none.

                Install it on the `HttpClient` the `Ktorfit` instance is built with.
                """.trimIndent(),
            ).initializer(body)
            .build()

    return FileSpec
        .builder(options.utilPackage, "ApiAuth")
        .addFileComment(GENERATED_COMMENT)
        .addType(authConfigType(model, options))
        .apply { if (model.needsBasicCredentials()) addType(basicCredentialsType()) }
        .addProperty(plugin)
        .build()
}

/** One `when` branch: where this scheme's credential goes on the request. */
private fun SecurityScheme.attach(): CodeBlock {
    val builder = CodeBlock.builder().beginControlFlow("%S ->", name)
    when (kind) {
        SecurityKind.HttpBearer, SecurityKind.OAuthToken -> {
            builder.addStatement(
                "credentials.%N?.invoke()?.let { request.headers.append(%S, %P) }",
                propertyName,
                "Authorization",
                "Bearer \$it",
            )
        }

        SecurityKind.HttpBasic -> {
            builder
                .addStatement("val basic = credentials.%N?.invoke()", propertyName)
                .beginControlFlow("if (basic != null)")
                .addStatement("val encoded = %T.encode(%P.encodeToByteArray())", BASE64, "\${basic.username}:\${basic.password}")
                .addStatement("request.headers.append(%S, %P)", "Authorization", "Basic \$encoded")
                .endControlFlow()
        }

        SecurityKind.ApiKeyHeader -> {
            builder.addStatement(
                "credentials.%N?.invoke()?.let { request.headers.append(%S, it) }",
                propertyName,
                parameterName.orEmpty(),
            )
        }

        SecurityKind.ApiKeyQuery -> {
            builder.addStatement(
                "credentials.%N?.invoke()?.let { request.url.parameters.append(%S, it) }",
                propertyName,
                parameterName.orEmpty(),
            )
        }

        SecurityKind.ApiKeyCookie -> {
            builder.addStatement(
                "credentials.%N?.invoke()?.let { request.headers.append(%S, %P) }",
                propertyName,
                "Cookie",
                "${parameterName.orEmpty()}=\$it",
            )
        }

        SecurityKind.Unsupported -> {
            error("a scheme with no credential slot should not be here")
        }
    }
    return builder.endControlFlow().build()
}

private val CLIENT_PLUGIN = ClassName("io.ktor.client.plugins.api", "ClientPlugin")
private val CREATE_CLIENT_PLUGIN = MemberName("io.ktor.client.plugins.api", "createClientPlugin")
private val ANNOTATIONS = MemberName("de.jensklingenberg.ktorfit", "annotations")

// Stable stdlib since Kotlin 2.2, so the generated code needs no opt-in and no dependency.
private val BASE64 = ClassName("kotlin.io.encoding", "Base64")
