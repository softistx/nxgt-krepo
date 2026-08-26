package com.strange.openapi

/**
 * A schema reduced to the kind of Kotlin type it becomes.
 *
 * Deliberately nominal rather than structural: [ModelRef] names a declaration in the model package
 * without saying what kind of declaration it is, so a data class, an enum and a sealed union are
 * all referred to the same way.
 *
 * Nullability is *not* modelled here. It belongs to the use site — a [Field] or a [Param] — and
 * wrapping it into the type would force every consumer to unwrap before it could match.
 */
public sealed interface TypeRef {
    public data object StringRef : TypeRef

    public data object IntRef : TypeRef

    public data object LongRef : TypeRef

    public data object DoubleRef : TypeRef

    public data object BooleanRef : TypeRef

    /** `string` with `format: date-time`. */
    public data object InstantRef : TypeRef

    /** `string` with `format: date` — a calendar day, with no time and no zone. */
    public data object LocalDateRef : TypeRef

    /** `string` with `format: uuid`. */
    public data object UuidRef : TypeRef

    /** A schema with no declared properties, carried as raw JSON. */
    public data object JsonObjectRef : TypeRef

    /** Binary payload, e.g. a `multipart/form-data` file part. */
    public data object BinaryRef : TypeRef

    /** No response body. */
    public data object UnitRef : TypeRef

    public data class ListRef(
        val element: TypeRef,
    ) : TypeRef

    /**
     * An object whose keys are open but whose values are typed — `additionalProperties` with a
     * schema. Without a schema the object is free-form and becomes [JsonObjectRef] instead.
     */
    public data class MapRef(
        val value: TypeRef,
    ) : TypeRef

    /** Reference to a generated model declaration by its simple name. */
    public data class ModelRef(
        val name: String,
    ) : TypeRef
}
