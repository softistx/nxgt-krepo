package com.strange.openapi.parser

/**
 * The `x-*` vocabulary this generator understands, and the typed reads of it.
 *
 * Keeping the key names and the accessors in one file is deliberate: [ExtensionNamespace] validates
 * every `x-kotlin-*` key a document carries against [Ext.kotlinKeys], and a registry that lived
 * apart from the code reading it would drift the first time a key was added.
 *
 * A value of the wrong type is a build failure rather than a silent skip. `x-kotlin-name: 42` says
 * something the author meant; generating as though they had said nothing is the failure mode this
 * generator exists to avoid.
 */
internal object Ext {
    /** Our namespace. A key inside it that this generator does not implement is a typo, not a hint. */
    const val KOTLIN_PREFIX: String = "x-kotlin-"

    const val NAME: String = "x-kotlin-name"

    /** Not in our namespace: `x-deprecated-reason` is written by several toolchains already. */
    const val DEPRECATED_REASON: String = "x-deprecated-reason"

    /** Every key in [KOTLIN_PREFIX] this generator implements. Adding a key means adding it here. */
    val kotlinKeys: Set<String> = setOf(NAME)
}

/**
 * The Kotlin name the document asks for, in place of the one this generator would derive.
 *
 * Validated as an identifier here rather than discovered as a compile error in generated source:
 * `x-kotlin-name: order id` would otherwise fail inside a file nobody wrote.
 */
internal fun Map<String, Any?>?.kotlinName(where: String): String? {
    val value = extensionString(Ext.NAME, where) ?: return null
    if (!IDENTIFIER.matches(value)) {
        throw OpenApiParseException(
            "$where: ${Ext.NAME} is '$value', which is not a legal Kotlin identifier. " +
                "Use letters, digits and underscores, starting with a letter or an underscore.",
        )
    }
    return value
}

/** The text that goes inside `@Deprecated`, in place of this generator's boilerplate. */
internal fun Map<String, Any?>?.deprecatedReason(where: String): String? =
    extensionString(Ext.DEPRECATED_REASON, where)?.trim()?.takeIf { it.isNotEmpty() }

internal fun Map<String, Any?>?.extensionString(
    key: String,
    where: String,
): String? {
    val value = this?.get(key) ?: return null
    return value as? String
        ?: throw OpenApiParseException("$where: $key must be a string, but the document gives ${describe(value)}")
}

internal fun describe(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> "a string"
        is Boolean -> "a boolean"
        is Number -> "a number"
        is List<*> -> "a list"
        is Map<*, *> -> "an object"
        else -> value::class.simpleName ?: "an unrecognised value"
    }

private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
