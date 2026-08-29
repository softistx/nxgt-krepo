package com.strange.openapi.emit

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.strange.openapi.ApiModel
import com.strange.openapi.SecurityKind
import com.strange.openapi.SecurityScheme

internal const val AUTH_CONFIG: String = "ApiAuthConfig"

internal const val BASIC_CREDENTIALS: String = "BasicCredentials"

/**
 * The schemes a generated client can actually attach a credential for.
 *
 * An `http` scheme this generator does not know — `digest`, `negotiate` — is left out: its
 * credential is the answer to a challenge, not a value the caller holds, and offering a slot for
 * one would be offering something that cannot work.
 */
internal fun ApiModel.credentialSchemes(): List<SecurityScheme> = securitySchemes.filter { it.kind != SecurityKind.Unsupported }

/** Schemes an operation requires that no client can satisfy. */
internal fun ApiModel.requireEverySchemeSatisfiable() {
    val unsupported = securitySchemes.filter { it.kind == SecurityKind.Unsupported }.associateBy { it.name }
    if (unsupported.isEmpty()) return
    val offenders =
        groups.flatMap { group ->
            group.operations.flatMap { operation ->
                operation.security.filter { it.scheme in unsupported }.map { "${group.name}.${operation.name} requires '${it.scheme}'" }
            }
        }
    if (offenders.isEmpty()) return
    throw EmitException(
        offenders.joinToString(
            prefix = "no generated client can satisfy these security schemes: ",
            separator = "; ",
        ) + ". Their credential is the answer to a server challenge rather than a value a caller holds.",
    )
}

/**
 * `ApiAuthConfig`: one credential slot per scheme the document declares.
 *
 * A slot is a suspending function rather than a value, because the interesting credentials expire:
 * a token read once at construction is a client that works until it does not. Returning null means
 * "no credential right now", and the request goes out without one — the server, not this client,
 * is what decides whether that is allowed.
 *
 * Every declared scheme gets a slot, including ones no operation currently requires. A document
 * that declares a scheme is describing a surface its author expects to use.
 */
internal fun authConfigType(
    model: ApiModel,
    options: EmitOptions,
): TypeSpec {
    val builder =
        TypeSpec
            .classBuilder(AUTH_CONFIG)
            .addModifiers(KModifier.PUBLIC)
            .addKdoc(
                """
                Where a client is given its credentials, one slot per scheme the document declares.

                Each slot is called per request that needs it, so a token that expires can be
                refreshed behind it. A slot left null, or returning null, sends no credential.
                """.trimIndent(),
            )

    model.credentialSchemes().forEach { scheme ->
        builder.addProperty(
            PropertySpec
                .builder(scheme.propertyName, supplierOf(scheme, options))
                .mutable()
                .initializer("null")
                .addKdoc("%L", scheme.slotDoc())
                .build(),
        )
    }
    return builder.build()
}

/** `BasicCredentials`, emitted only for a document that declares a basic scheme. */
internal fun basicCredentialsType(): TypeSpec =
    TypeSpec
        .classBuilder(BASIC_CREDENTIALS)
        .addModifiers(KModifier.PUBLIC, KModifier.DATA)
        .addKdoc("A username and password, which this client encodes for the `Basic` scheme.")
        .primaryConstructor(
            FunSpec
                .constructorBuilder()
                .addParameter("username", STRING)
                .addParameter("password", STRING)
                .build(),
        ).addProperty(PropertySpec.builder("username", STRING).initializer("username").build())
        .addProperty(PropertySpec.builder("password", STRING).initializer("password").build())
        .build()

internal fun ApiModel.needsBasicCredentials(): Boolean = credentialSchemes().any { it.kind == SecurityKind.HttpBasic }

private fun supplierOf(
    scheme: SecurityScheme,
    options: EmitOptions,
): TypeName {
    val credential =
        if (scheme.kind == SecurityKind.HttpBasic) ClassName(options.utilPackage, BASIC_CREDENTIALS) else STRING
    return LambdaTypeName
        .get(returnType = credential.copy(nullable = true))
        .copy(suspending = true, nullable = true)
}

private fun SecurityScheme.slotDoc(): String {
    val what =
        when (kind) {
            SecurityKind.HttpBearer -> {
                "Sent as `Authorization: Bearer <token>`."
            }

            SecurityKind.OAuthToken -> {
                "An access token, sent as `Authorization: Bearer <token>`. The document describes " +
                    "a flow; running one is not something this generator can do, so the token is " +
                    "yours to obtain and hand over."
            }

            SecurityKind.HttpBasic -> {
                "Encoded and sent as `Authorization: Basic <base64>`."
            }

            SecurityKind.ApiKeyHeader -> {
                "Sent as the `$parameterName` header."
            }

            SecurityKind.ApiKeyQuery -> {
                "Sent as the `$parameterName` query parameter."
            }

            SecurityKind.ApiKeyCookie -> {
                "Sent as the `$parameterName` cookie."
            }

            SecurityKind.Unsupported -> {
                ""
            }
        }
    return listOfNotNull("Credential for the document's `$name` scheme.", "", what, description?.let { "\n$it" })
        .joinToString("\n")
}
