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

/**
 * A schema constrained to a fixed set of values: one `enum class`.
 *
 * Generated enums are *tolerant* — they carry a fallback entry for values the document does not
 * list, so a server that deploys a new value does not break clients compiled against the old
 * document. The raw unlisted value does not survive: an enum constant is a singleton with nowhere
 * to keep it.
 */
public data class EnumType(
    override val name: String,
    val entries: List<EnumEntry>,
    /** The scalar the values are written as: [TypeRef.StringRef], [TypeRef.IntRef] or [TypeRef.LongRef]. */
    val base: TypeRef,
    /**
     * Entry standing for a value the document does not list.
     *
     * Its wire value is a sentinel the server will reject rather than a plausible one: a caller
     * that reads an object holding an unknown value and writes it back unchanged then fails at the
     * server with a clear error, instead of quietly rewriting the field to something wrong.
     */
    val fallback: EnumEntry,
) : ModelType

public data class EnumEntry(
    val name: String,
    /** The value as the document writes it — not necessarily a valid Kotlin identifier. */
    val wireValue: String,
)
