package dev.nxgt.openapi

/**
 * Frontend-agnostic description of a client to generate.
 *
 * Emitters ([ClientEmitter]) consume this; nothing here knows about Ktorfit or Spring,
 * so a second emitter can be added without touching the parser.
 */
public data class ClientModel(
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
)

public data class ModelType(
    val name: String,
    val fields: List<Field>,
)

public data class Field(
    val name: String,
    val wireName: String,
    val type: TypeRef,
    val required: Boolean,
)

/** How operations are split into interfaces. */
public enum class Grouping { Tag, Path, None }

public sealed interface TypeRef {
    public data object StringRef : TypeRef
    public data object IntRef : TypeRef
    public data object LongRef : TypeRef
    public data object DoubleRef : TypeRef
    public data object BooleanRef : TypeRef

    /** `string` with `format: date-time`. */
    public data object InstantRef : TypeRef

    /** A schema with no declared properties, carried as raw JSON. */
    public data object JsonObjectRef : TypeRef

    /** Binary payload, e.g. a `multipart/form-data` file part. */
    public data object BinaryRef : TypeRef

    /** No response body. */
    public data object UnitRef : TypeRef

    public data class ListRef(val element: TypeRef) : TypeRef

    /** Reference to a generated model class by its simple name. */
    public data class ModelRef(val name: String) : TypeRef
}
