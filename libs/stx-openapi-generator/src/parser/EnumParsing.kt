package com.strange.openapi.parser

import com.strange.openapi.EnumEntry
import com.strange.openapi.EnumType
import com.strange.openapi.TypeRef
import io.swagger.v3.oas.models.media.Schema

/**
 * Turning a constrained schema into an [EnumType].
 *
 * The values a document lists are wire values, not Kotlin names, so every one of them has to be
 * derived into an identifier — and two that derive to the same identifier are a collision, reported
 * the same way as any other, rather than one quietly winning.
 */
internal fun enumTypeOf(
    schema: Schema<*>,
    name: String,
    where: String,
): EnumType {
    val base = enumBase(schema, where)
    val values = schema.enum.orEmpty()
    // Indices are kept against the raw list: `x-enum-varnames` lines up with `enum` as the document
    // writes it, and a null entry in it is nullability rather than a value to name.
    val names = schema.extensions.enumEntryNames(where)?.alignedWith(values, Ext.ENUM_VARNAMES, where)
    val docs = schema.extensions.enumDescriptions(where)?.alignedWith(values, Ext.ENUM_DESCRIPTIONS, where)
    val entries =
        values.withIndex().filter { it.value != null }.map { (index, value) ->
            EnumEntry(
                name =
                    names?.get(index)?.also { requireIdentifier(it, Ext.ENUM_VARNAMES, where) }
                        ?: Naming.enumEntry(value.toString()),
                wireValue = value.toString(),
                doc = docs?.get(index)?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    if (entries.isEmpty()) throw OpenApiParseException("$where: enum lists no values")
    entries.requireDistinctEntryNames(where)
    return EnumType(
        name = name,
        entries = entries,
        doc = schema.doc(),
        base = base,
        fallback = fallbackEntry(entries, base),
    )
}

/**
 * Whether this schema's `enum` can become an enum class at all.
 *
 * Only strings and integers can: a floating-point enum is a bad idea, and a mixed-type list has no
 * single Kotlin type to hold it. Those keep the underlying scalar instead, which is a visible
 * limitation rather than a wrong one.
 */
internal fun Schema<*>.hasGeneratableEnum(): Boolean {
    if (enum.isNullOrEmpty()) return false
    return when (type ?: types?.firstOrNull { it != "null" }) {
        "string", "integer", null -> enum.filterNotNull().isNotEmpty()
        else -> false
    }
}

private fun enumBase(
    schema: Schema<*>,
    where: String,
): TypeRef =
    when (val type = schema.type ?: schema.types?.firstOrNull { it != "null" }) {
        "string", null -> TypeRef.StringRef

        "integer" -> if (schema.format == "int64") TypeRef.LongRef else TypeRef.IntRef

        else -> throw OpenApiParseException(
            "$where: an enum of '$type' values cannot be generated; only string and integer enums can",
        )
    }

/**
 * The fallback entry, named and valued so it cannot be mistaken for a real one.
 *
 * The Kotlin name moves out of the way if the document happens to use it — a real value always
 * keeps the plain name. The wire value is a sentinel no server will accept, which is the point:
 * writing back a value this client could not name should fail, not succeed with invented data.
 */
private fun fallbackEntry(
    entries: List<EnumEntry>,
    base: TypeRef,
): EnumEntry {
    val takenNames = entries.mapTo(mutableSetOf()) { it.name }
    val takenValues = entries.mapTo(mutableSetOf()) { it.wireValue }
    val name = generateSequence("UNKNOWN") { "${it}_" }.first { it !in takenNames }
    val value =
        when (base) {
            TypeRef.IntRef -> generateSequence(Int.MIN_VALUE) { it + 1 }.first { "$it" !in takenValues }.toString()
            TypeRef.LongRef -> generateSequence(Long.MIN_VALUE) { it + 1 }.first { "$it" !in takenValues }.toString()
            else -> generateSequence("__unknown__") { "${it}_" }.first { it !in takenValues }
        }
    return EnumEntry(name = name, wireValue = value)
}

private fun List<EnumEntry>.requireDistinctEntryNames(where: String) {
    val duplicates = groupBy { it.name }.filterValues { it.size > 1 }
    if (duplicates.isEmpty()) return
    throw OpenApiParseException(
        duplicates.entries.joinToString(
            prefix = "$where: enum values collide on a generated entry name: ",
            separator = "; ",
        ) { (entryName, colliding) ->
            "$entryName (from ${colliding.joinToString(", ") { "'${it.wireValue}'" }})"
        } + ". Rename the values so their Kotlin names differ.",
    )
}

/**
 * A parallel list, checked against the values it is parallel to.
 *
 * A shorter list would silently rename the wrong entries and leave the rest derived, which is worse
 * than either doing nothing or failing — the generated enum would look deliberate and be wrong.
 */
private fun List<String>.alignedWith(
    values: List<Any?>,
    key: String,
    where: String,
): List<String> {
    if (size == values.size) return this
    throw OpenApiParseException(
        "$where: $key has $size entries but the enum lists ${values.size}. " +
            "They are read in order, so the lists have to be the same length.",
    )
}
