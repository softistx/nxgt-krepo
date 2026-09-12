package com.softistx.openapi.emit

import com.softistx.openapi.Param
import com.softistx.openapi.TypeRef

/** How a value that may be absent or null is written in Kotlin. */
public data class Optionality(
    val nullable: Boolean,
    /** Kotlin source for the declaration's default, or null for no default. */
    val defaultSource: String?,
)

/**
 * The one rule deciding whether a generated declaration is nullable and what it defaults to.
 *
 * Model properties, named parameters and multipart parts all ask the same question, and they must
 * not answer it differently — a property that is nullable in the model but not in the operation
 * that sends it would not compile against its own client.
 *
 * The two spec facts are independent. `required` says whether the value may be *absent*; `nullable`
 * says whether it may be *null*. A required property can be nullable, and then it has no default:
 * the caller must pass something, and `null` is something.
 */
public fun optionalityOf(
    type: TypeRef,
    required: Boolean,
    nullable: Boolean,
    default: String?,
): Optionality {
    // A default only helps where the value may be omitted. Giving one to a required declaration
    // would let callers skip a value the server insists on.
    val rendered = if (required) null else default?.let { defaultSource(it, type) }
    return Optionality(
        // Absent with no default has to be expressible, so it becomes null.
        nullable = nullable || (!required && rendered == null),
        defaultSource = rendered ?: "null".takeIf { !required },
    )
}

/**
 * The document's default, as Kotlin source — or null where it cannot be one.
 *
 * Only scalars are rendered. A default for a model, a list or a map would have to be constructed
 * rather than written, and a wrong one is worse than none; those fall back to `null` and are called
 * out in the generator README.
 */
private fun defaultSource(
    literal: String,
    type: TypeRef,
): String? =
    when (type) {
        TypeRef.StringRef -> "\"${literal.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        TypeRef.IntRef -> literal.toIntOrNull()?.toString()
        TypeRef.LongRef -> literal.toLongOrNull()?.let { "${it}L" }
        TypeRef.DoubleRef -> literal.toDoubleOrNull()?.toString()
        TypeRef.BooleanRef -> literal.toBooleanStrictOrNull()?.toString()
        else -> null
    }

/** [optionalityOf] for a parameter, which carries all four facts itself. */
public val Param.optionality: Optionality
    get() = optionalityOf(type, required, nullable, default)
