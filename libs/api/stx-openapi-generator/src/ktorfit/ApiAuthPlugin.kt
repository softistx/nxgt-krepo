package com.softistx.openapi.ktorfit

import com.softistx.openapi.ApiModel
import com.softistx.openapi.SecurityKind
import com.softistx.openapi.SecurityScheme
import com.softistx.openapi.emit.AUTH_CONFIG
import com.softistx.openapi.emit.EmitOptions
import com.softistx.openapi.emit.GENERATED_COMMENT
import com.softistx.openapi.emit.apiOperationName
import com.softistx.openapi.emit.authConfigType
import com.softistx.openapi.emit.basicCredentialsType
import com.softistx.openapi.emit.chooseAlternative
import com.softistx.openapi.emit.credentialSchemes
import com.softistx.openapi.emit.needsBasicCredentials
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec

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
 * ktorfit-ksp carries through from the generated interface. The requirement is a list of
 * alternatives, and [chooseAlternative] holds the rule for picking one.
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
                "val operation = request.%M.filterIsInstance<%T>().firstOrNull() ?: return@onRequest",
                ANNOTATIONS,
                apiOperationName(options),
            ).addStatement("val alternatives = operation.security")
            .addStatement("if (alternatives.isEmpty()) return@onRequest")
            .add(chooseAlternative(schemes, "alternatives", "operation.id") { it.attach() })
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

                `security` is a list of alternatives: the first one whose credentials are all
                configured is sent, and only those. When none is configured and the document allows
                anonymous access (`{}`) nothing is sent; otherwise the request fails with an
                [IllegalStateException] naming the expected alternatives, before it leaves.

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

/** The statements that put this scheme's credential, held in `value`, on the request. */
private fun SecurityScheme.attach(): CodeBlock {
    val builder = CodeBlock.builder()
    when (kind) {
        SecurityKind.HttpBearer, SecurityKind.OAuthToken -> {
            builder.addStatement("request.headers.append(%S, %P)", "Authorization", "Bearer \$value")
        }

        SecurityKind.HttpBasic -> {
            builder
                .addStatement("val encoded = %T.encode(%P.encodeToByteArray())", BASE64, "\${value.username}:\${value.password}")
                .addStatement("request.headers.append(%S, %P)", "Authorization", "Basic \$encoded")
        }

        SecurityKind.ApiKeyHeader -> {
            builder.addStatement("request.headers.append(%S, value)", parameterName.orEmpty())
        }

        SecurityKind.ApiKeyQuery -> {
            builder.addStatement("request.url.parameters.append(%S, value)", parameterName.orEmpty())
        }

        SecurityKind.ApiKeyCookie -> {
            builder.addStatement("request.headers.append(%S, %P)", "Cookie", "${parameterName.orEmpty()}=\$value")
        }

        SecurityKind.Unsupported -> {
            error("a scheme with no credential slot should not be here")
        }
    }
    return builder.build()
}

private val CLIENT_PLUGIN = ClassName("io.ktor.client.plugins.api", "ClientPlugin")
private val CREATE_CLIENT_PLUGIN = MemberName("io.ktor.client.plugins.api", "createClientPlugin")
private val ANNOTATIONS = MemberName("de.jensklingenberg.ktorfit", "annotations")

// Stable stdlib since Kotlin 2.2, so the generated code needs no opt-in and no dependency.
private val BASE64 = ClassName("kotlin.io.encoding", "Base64")
