package com.strange.openapi.parser

import io.swagger.v3.oas.models.media.Schema

/**
 * Whether the document says this may be `null`.
 *
 * One of the facts a schema states about a *use* of a type rather than about the type itself, which
 * is why it lives beside [typeOf] rather than inside it. Three spellings mean the same thing: 3.0's
 * `nullable: true`, 3.1's `type: [string, "null"]`, and `x-nullable`, which is how a document
 * converted from Swagger 2 still says it. Neither is the same question as `required` — a required
 * property can still hold `null`.
 */
internal fun Schema<*>.isNullable(where: String = "schema"): Boolean =
    nullable == true ||
        types?.contains("null") == true ||
        extensions.extensionBoolean(Ext.NULLABLE, where) == true ||
        (oneOf ?: anyOf).orEmpty().any { it.isNullOnly() }

/**
 * The document's `default`, as it appears on the wire, or null when it gave none.
 *
 * An explicit `default: null` is treated as none: it says nothing that optionality does not already
 * say, and it appears in real documents on properties typed `string`, where it is not even valid.
 */
internal fun Schema<*>.defaultLiteral(): String? = default?.toString()

/** The document's `description`, trimmed, or null when it gave none worth carrying. */
internal fun Schema<*>.doc(): String? = description?.trim()?.takeIf { it.isNotEmpty() }
