package com.softistx.openapi.emit

import com.softistx.openapi.ApiModel
import com.softistx.openapi.Operation
import com.softistx.openapi.SecurityKind
import com.softistx.openapi.SecurityScheme
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

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

/**
 * The model with every alternative no client can satisfy taken out of the operations' `security`.
 *
 * An alternative is satisfiable when every scheme it names has a credential slot. One that names an
 * unsupported scheme — `digest`, `negotiate` — is dropped rather than failing the emit, because the
 * document offers the caller another way in: an operation that accepts `[{Digest}, {Bearer}]` is
 * perfectly callable with a bearer token, and refusing the whole document would be refusing an API
 * the client can in fact use. The generated client then never considers the dropped alternative:
 * it is not chosen, and it is not named in the error a client raises when it holds no credential.
 *
 * What is still refused is an operation left with **no** satisfiable alternative: there the client
 * has nothing it could send, and a silent `security` of nothing would read as "open". The anonymous
 * alternative `{}` counts as satisfiable, so `[{Digest}, {}]` is emitted as anonymous-only.
 * The failure names the operation and every scheme in the way.
 */
internal fun ApiModel.withSatisfiableSecurity(): ApiModel {
    val unsupported = securitySchemes.filter { it.kind == SecurityKind.Unsupported }.map { it.name }.toSet()
    if (unsupported.isEmpty()) return this
    val offenders = mutableListOf<String>()
    val kept =
        groups.map { group ->
            group.operations
                .map { operation ->
                    val usable = operation.security.filter { alternative -> alternative.none { it.scheme in unsupported } }
                    if (operation.security.isNotEmpty() && usable.isEmpty()) {
                        val blocking =
                            operation.security
                                .flatten()
                                .map { it.scheme }
                                .filter { it in unsupported }
                                .distinct()
                        offenders += "${group.name}.${operation.name} requires ${blocking.joinToString(", ") { "'$it'" }}"
                    }
                    operation.copy(security = usable)
                }.let { group.copy(operations = it) }
        }
    if (offenders.isNotEmpty()) {
        throw EmitException(
            offenders.joinToString(
                prefix = "no generated client can satisfy these security schemes: ",
                separator = "; ",
            ) + ". Their credential is the answer to a server challenge rather than a value a caller holds, " +
                "and these operations offer no other alternative.",
        )
    }
    return copy(groups = kept)
}

/** The character `@ApiOperation.security` joins the schemes of one alternative with. */
internal const val ALTERNATIVE_SEPARATOR: Char = ','

/**
 * An operation's alternatives as the strings `@ApiOperation(security = [...])` carries: one element
 * per alternative, its scheme names joined by [ALTERNATIVE_SEPARATOR], and `""` for the anonymous
 * alternative `{}`. An annotation cannot hold an array of arrays, hence the encoding.
 */
internal fun Operation.encodedSecurity(): List<String> =
    security.map { alternative ->
        alternative.joinToString(ALTERNATIVE_SEPARATOR.toString()) { requirement ->
            if (ALTERNATIVE_SEPARATOR in requirement.scheme) {
                throw EmitException(
                    "$name requires security scheme '${requirement.scheme}', whose name contains " +
                        "'$ALTERNATIVE_SEPARATOR', the character the generated annotation joins an alternative's " +
                        "schemes with. Rename the scheme.",
                )
            }
            requirement.scheme
        }
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
