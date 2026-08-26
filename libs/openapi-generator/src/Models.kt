package com.strange.openapi

/**
 * A declaration to generate in the model package.
 *
 * Sealed because a schema is not always a data class: the shapes a document can describe include
 * enumerations and unions, and each emits differently. Adding a variant is deliberately a compile
 * error everywhere that renders one.
 */
public sealed interface ModelType {
    public val name: String
}

/** A schema with declared properties: one data class. */
public data class ObjectType(
    override val name: String,
    val fields: List<Field>,
) : ModelType

public data class Field(
    val name: String,
    val wireName: String,
    val type: TypeRef,
    val required: Boolean,
    /**
     * The document says this may be `null`, which is independent of whether it is required:
     * a required property can still hold `null`, and an optional one can be absent but never null.
     */
    val nullable: Boolean = false,
    /** The document's `default`, as it appears on the wire. Null means the document gave none. */
    val default: String? = null,
)
