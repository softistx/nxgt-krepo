package com.strange.openapi

import com.strange.openapi.parser.Naming

/**
 * Frontend-agnostic description of a client to generate.
 *
 * Emitters consume this; nothing here knows about Ktorfit or Spring, so a second emitter can be
 * added without touching the parser.
 */
public data class ApiModel(
    val groups: List<ApiGroup>,
    val models: List<ModelType>,
    /**
     * Every scheme `components.securitySchemes` declares, whether or not an operation requires it.
     *
     * The whole list rather than only the used part: a client exposes one credential slot per
     * scheme, and a document that declares a scheme it never requires is describing an API surface
     * its author expects to grow.
     */
    val securitySchemes: List<SecurityScheme> = emptyList(),
)

/** A set of operations that become one interface — by default, one OpenAPI tag. */
public data class ApiGroup(
    val name: String,
    val operations: List<Operation>,
)

public data class Operation(
    /**
     * The document's own `operationId`, which OpenAPI requires to be unique across the document.
     *
     * Kept beside [name] rather than replaced by it: [name] is derived, and `x-kotlin-name` can
     * change it, so it is not a key. This is what generated code uses to say *which* operation a
     * response belongs to, and what a reader greps the document for.
     */
    val id: String,
    val name: String,
    val httpMethod: String,
    /** Path relative to the base URL, without a leading slash. */
    val path: String,
    /**
     * This operation's entry in the generated `Endpoints` object — `PATCH_ORDERS_ID_STATUS`.
     *
     * Separate from [name] because the two are derived from different things and so collide
     * independently: [name] comes from the `operationId`, which OpenAPI already requires to be
     * unique, while two paths differing only in a template brace reduce to one constant.
     *
     * Defaulted to the derivation rather than left required, because the parser is the only thing
     * that builds an [Operation] and would pass exactly this — the parameter exists for the one case
     * that differs, an `x-kotlin-endpoint` naming the constant outright.
     */
    val constant: String = Naming.endpointConstant(httpMethod, path),
    val parameters: List<Param>,
    val returnType: TypeRef,
    val summary: String? = null,
    val deprecated: Boolean = false,
    /** `x-deprecated-reason`, which replaces this generator's boilerplate inside `@Deprecated`. */
    val deprecatedReason: String? = null,
    /** Every non-2xx response the document declares for this operation, in status order. */
    val errors: List<ErrorResponse> = emptyList(),
    /**
     * The schemes a caller must satisfy, already resolved against the document root.
     *
     * Empty means *no* authentication, which the document can say two ways — by declaring nothing
     * anywhere, or by overriding the root with `security: []`. Both arrive here as an empty list,
     * because to a caller they are the same instruction.
     */
    val security: List<SecurityRequirement> = emptyList(),
)

/**
 * A response the caller does not want: a declared status that is not 2xx.
 *
 * Modelled apart from [Operation.returnType] rather than folded into it, because the two have
 * opposite obligations — a return type is what a function produces, and this is what it fails with.
 */
public data class ErrorResponse(
    /** `"404"`, or `"default"` for the catch-all the document declares last. */
    val status: String,
    /**
     * Null when the document declares the status but no body for it — a bare `401` is still a
     * documented `401`, and dropping it would lose the only record that the operation can fail.
     */
    val type: TypeRef?,
    val description: String? = null,
) {
    /** The numeric status, or null for `default`. */
    public val code: Int? get() = status.toIntOrNull()
}

/** One `security` entry: a scheme by the name `securitySchemes` gives it, and its scopes. */
public data class SecurityRequirement(
    val scheme: String,
    val scopes: List<String> = emptyList(),
)

/**
 * What a scheme asks a caller for, reduced to where the credential goes.
 *
 * `oauth2` and `openIdConnect` become [OAuthToken] rather than being refused: this generator
 * cannot *run* a flow, but the thing a flow produces is an access token, and RFC 6749 sends it in
 * the same `Authorization: Bearer` header as [HttpBearer]. A client that is handed a token can
 * therefore use one, and refusing the whole document would be refusing an API it can in fact call.
 *
 * [Unsupported] is what is left: an `http` scheme this generator does not know — `digest`,
 * `negotiate` — where the credential is the answer to a challenge rather than a value a caller
 * holds. It is carried rather than rejected at parse time, so a document that *declares* one
 * without requiring it still parses.
 */
public enum class SecurityKind {
    HttpBearer,
    HttpBasic,
    ApiKeyHeader,
    ApiKeyQuery,
    ApiKeyCookie,
    OAuthToken,
    Unsupported,
}

public data class SecurityScheme(
    /** The document's own name for it — `Bearer`, `Basic` — not a derived one. */
    val name: String,
    /**
     * The Kotlin name of the credential slot a client offers for it — `Bearer` -> `bearer`.
     *
     * Derived by the parser, like every other Kotlin name in this model, so an emitter never has
     * to decide what a document's word looks like in Kotlin.
     */
    val propertyName: String,
    val kind: SecurityKind,
    /** For [SecurityKind.ApiKeyHeader] and [SecurityKind.ApiKeyQuery]: the name the key is sent under. */
    val parameterName: String? = null,
    val description: String? = null,
)

public enum class ParamKind { Path, Query, Header, Body, Part }

public data class Param(
    /** Kotlin parameter name. */
    val name: String,
    /**
     * Name as it appears on the wire, which may not be a valid Kotlin identifier — and which
     * [name] is free to differ from, whether because it was derived or because `x-kotlin-name`
     * said so.
     */
    val wireName: String,
    val kind: ParamKind,
    val type: TypeRef,
    val required: Boolean,
    /**
     * The document says this may be `null` — which is not the same question as [required].
     * A required parameter that is nullable must still be sent, and may be sent as `null`.
     */
    val nullable: Boolean = false,
    /** The document's `default`, as it appears on the wire. Null means the document gave none. */
    val default: String? = null,
)
