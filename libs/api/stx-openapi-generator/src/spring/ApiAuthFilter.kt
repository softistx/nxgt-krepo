package com.softistx.openapi.spring

import com.softistx.openapi.ApiModel
import com.softistx.openapi.SecurityKind
import com.softistx.openapi.SecurityScheme
import com.softistx.openapi.emit.AUTH_CONFIG
import com.softistx.openapi.emit.EmitOptions
import com.softistx.openapi.emit.GENERATED_COMMENT
import com.softistx.openapi.emit.authConfigType
import com.softistx.openapi.emit.basicCredentialsType
import com.softistx.openapi.emit.chooseAlternative
import com.softistx.openapi.emit.credentialSchemes
import com.softistx.openapi.emit.needsBasicCredentials
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING

/**
 * `ApiAuth.kt`: the Spring filter that attaches the credential each operation asks for.
 *
 * The document knows which operations need which scheme and which are open — this one declares
 * `Bearer` at its root and overrides it with `security: []` on the operations that hand out the
 * token in the first place — and until now none of that reached the client. A hand-written filter
 * either sends the header everywhere, including to the sign-in endpoint, or keeps its own copy of
 * the list.
 *
 * It reads the requirement out of the attribute `apiOperationProcessor()` put there; by the time a
 * `ClientRequest` exists the method, and so the annotation, is gone.
 *
 * The credential slots are the same `ApiAuthConfig` the Ktorfit client uses, because a credential
 * is a fact about the document rather than about the client style.
 *
 * ```kotlin
 * val credentials = ApiAuthConfig().apply { bearer = { tokenStore.current() } }
 * WebClient.builder().filter(apiAuthFilter(credentials)).build()
 * ```
 */
internal fun apiAuthFile(
    model: ApiModel,
    options: EmitOptions,
): FileSpec? {
    val schemes = model.credentialSchemes()
    if (schemes.isEmpty()) return null

    val filter =
        FunSpec
            .builder("apiAuthFilter")
            .addModifiers(KModifier.PUBLIC)
            .addParameter("credentials", ClassName(options.utilPackage, AUTH_CONFIG))
            .returns(EXCHANGE_FILTER)
            .addKdoc(
                """
                Attaches the credential each operation's `security` asks for, and nothing to the
                operations that ask for none.

                Add it to the `WebClient` the proxy is built on, and give the proxy factory
                [apiOperationProcessor] — without the processor this filter sees a request with no
                idea which operation made it, and attaches nothing.

                A credential slot suspends and a filter does not, so each is resolved inside
                `mono { }`: the credential is fetched when the request is made, not once at startup,
                which is what lets a token expire and be replaced.

                `security` is a list of alternatives: the first one whose credentials are all
                configured is sent, and only those. When none is configured and the document allows
                anonymous access (`{}`) nothing is sent; otherwise the call fails with an
                [IllegalStateException] naming the expected alternatives, before it leaves.
                """.trimIndent(),
            ).addCode(
                CodeBlock
                    .builder()
                    .beginControlFlow("return %T { request, next ->", EXCHANGE_FILTER)
                    .addStatement(
                        "val alternatives = request.attributes()[%L] as %T?",
                        SECURITY_ATTRIBUTE,
                        LIST.parameterizedBy(STRING),
                    ).addStatement("val operation = request.attributes()[%L] as String?", OPERATION_ATTRIBUTE)
                    .beginControlFlow("if (alternatives.isNullOrEmpty()) next.exchange(request) else")
                    .beginControlFlow("%M(%T.Unconfined) {", MONO_BUILDER, DISPATCHERS)
                    .addStatement("val authorized = %T.from(request)", CLIENT_REQUEST)
                    .addStatement("val url = %T.fromUri(request.url())", URI_BUILDER)
                    .add(chooseAlternative(schemes, "alternatives", "operation") { it.attach() })
                    .addStatement("authorized.url(url.build(true).toUri()).build()")
                    .endControlFlow()
                    .addStatement(".flatMap { next.exchange(it) }")
                    .endControlFlow()
                    .endControlFlow()
                    .build(),
            ).build()

    return FileSpec
        .builder(options.utilPackage, "ApiAuth")
        .addFileComment(GENERATED_COMMENT)
        .addType(authConfigType(model, options))
        .apply { if (model.needsBasicCredentials()) addType(basicCredentialsType()) }
        .addFunction(filter)
        .build()
}

/** The statements that put this scheme's credential, held in `value`, on the request. */
private fun SecurityScheme.attach(): CodeBlock {
    val builder = CodeBlock.builder()
    when (kind) {
        SecurityKind.HttpBearer, SecurityKind.OAuthToken -> {
            builder.addStatement("authorized.header(%S, %P)", "Authorization", "Bearer \$value")
        }

        SecurityKind.HttpBasic -> {
            builder
                .addStatement("val encoded = %T.encode(%P.encodeToByteArray())", BASE64, "\${value.username}:\${value.password}")
                .addStatement("authorized.header(%S, %P)", "Authorization", "Basic \$encoded")
        }

        SecurityKind.ApiKeyHeader -> {
            builder.addStatement("authorized.header(%S, value)", parameterName.orEmpty())
        }

        // A ClientRequest.Builder cannot append to the query string, so the URL is rebuilt around
        // it and handed back through `url(...)`.
        SecurityKind.ApiKeyQuery -> {
            builder.addStatement("url.queryParam(%S, value)", parameterName.orEmpty())
        }

        SecurityKind.ApiKeyCookie -> {
            builder.addStatement("authorized.cookie(%S, value)", parameterName.orEmpty())
        }

        SecurityKind.Unsupported -> {
            error("a scheme with no credential slot should not be here")
        }
    }
    return builder.build()
}

private val EXCHANGE_FILTER = ClassName("org.springframework.web.reactive.function.client", "ExchangeFilterFunction")
private val CLIENT_REQUEST = ClassName("org.springframework.web.reactive.function.client", "ClientRequest")
private val URI_BUILDER = ClassName("org.springframework.web.util", "UriComponentsBuilder")
private val MONO_BUILDER = MemberName("kotlinx.coroutines.reactor", "mono")
private val DISPATCHERS = ClassName("kotlinx.coroutines", "Dispatchers")
private val LIST = ClassName("kotlin.collections", "List")

// Stable stdlib since Kotlin 2.2, so the generated code needs no opt-in and no dependency.
private val BASE64 = ClassName("kotlin.io.encoding", "Base64")
