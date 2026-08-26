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
    /** The schema's `description`, if it gave one. */
    val doc: String? = null,
    val deprecated: Boolean = false,
    /**
     * Union bases this schema is a member of, empty for a standalone schema.
     *
     * The link comes from a `oneOf` naming this schema, never from this schema's own `allOf`:
     * inheriting a base's fields does not make a hierarchy closed, and only the `oneOf` says it is.
     */
    val implements: List<String> = emptyList(),
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
    /** The property's `description`, if it gave one. */
    val doc: String? = null,
    val deprecated: Boolean = false,
    /** True when this field realises a property the union base already declares. */
    val overrides: Boolean = false,
    /**
     * A value this field always holds — a discriminator's tag.
     *
     * Unlike [default] it is not a suggestion: it identifies the subtype, so it is always written.
     */
    val constant: String? = null,
)

/**
 * A `oneOf` or `anyOf` over object schemas: one sealed interface.
 *
 * Only over object schemas. A sealed hierarchy needs its members to implement an interface, and
 * neither `String` nor `List` can — a union over scalars stays raw JSON rather than becoming a type
 * that cannot be deserialized.
 */
public data class UnionType(
    override val name: String,
    val subtypes: List<UnionSubtype>,
    /** Null when the document declared no `discriminator`, which means members are told apart by shape. */
    val discriminator: UnionDiscriminator?,
    /**
     * Generated catch-all subtype for a tag the document does not list.
     *
     * Only a discriminated union has one: with no discriminator there is nothing to put in it.
     */
    val fallback: String?,
) : ModelType

public data class UnionSubtype(
    /** Name of the [ObjectType] that implements the base. */
    val name: String,
    /** The discriminator value selecting this subtype, or null in a union told apart by shape. */
    val wireValue: String?,
    /** Wire names no sibling declares, for a union that has to be told apart by shape. */
    val distinguishingKeys: List<String> = emptyList(),
)

public data class UnionDiscriminator(
    /** Kotlin property name on the base interface. */
    val name: String,
    val wireName: String,
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
    /** The schema's `description`, if it gave one. */
    val doc: String? = null,
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
