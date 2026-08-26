package com.strange.openapi.parser

import io.swagger.v3.oas.models.media.Schema

/**
 * Whether the document says this may be `null`.
 *
 * One of the facts a schema states about a *use* of a type rather than about the type itself, which
 * is why it lives beside [typeOf] rather than inside it. Two spellings mean the same thing: 3.0's
 * `nullable: true`, and 3.1's `type: [string, "null"]`. Neither is the same question as `required`
 * — a required property can still hold `null`.
 */
internal fun Schema<*>.isNullable(): Boolean =
    nullable == true ||
        types?.contains("null") == true ||
        (oneOf ?: anyOf).orEmpty().any { it.isNullOnly() }

/**
 * The document's `default`, as it appears on the wire, or null when it gave none.
 *
 * An explicit `default: null` is treated as none: it says nothing that optionality does not already
 * say, and it appears in real documents on properties typed `string`, where it is not even valid.
 */
internal fun Schema<*>.defaultLiteral(): String? = default?.toString()
