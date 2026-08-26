package com.strange.openapi

/**
 * Frontend-agnostic description of a client to generate.
 *
 * Emitters consume this; nothing here knows about Ktorfit or Spring, so a second emitter can be
 * added without touching the parser.
 */
public data class ApiModel(
    val groups: List<ApiGroup>,
    val models: List<ModelType>,
)

/** A set of operations that become one interface — by default, one OpenAPI tag. */
public data class ApiGroup(
    val name: String,
    val operations: List<Operation>,
)

public data class Operation(
    val name: String,
    val httpMethod: String,
    /** Path relative to the base URL, without a leading slash. */
    val path: String,
    val parameters: List<Param>,
    val returnType: TypeRef,
    val summary: String? = null,
)

public enum class ParamKind { Path, Query, Header, Body, Part }

public data class Param(
    /** Kotlin parameter name. */
    val name: String,
    /** Name as it appears on the wire, which may not be a valid Kotlin identifier. */
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
