package com.strange.openapi.parser

/**
 * The `x-*` vocabulary this generator understands, and the typed reads of it.
 *
 * Keeping the key names and the accessors in one file is deliberate: [requireKnownKotlinExtensions]
 * validates every `x-kotlin-*` key a document carries against [Ext.kotlinKeys], and a registry that
 * lived apart from the code reading it would drift the first time a key was added.
 *
 * A value of the wrong type is a build failure rather than a silent skip. `x-kotlin-name: 42` says
 * something the author meant; generating as though they had said nothing is the failure mode this
 * generator exists to avoid.
 */
internal object Ext {
    /** Our namespace. A key inside it that this generator does not implement is a typo, not a hint. */
    const val KOTLIN_PREFIX: String = "x-kotlin-"

    const val NAME: String = "x-kotlin-name"

    const val SKIP: String = "x-kotlin-skip"

    const val TYPE: String = "x-kotlin-type"

    const val VALUE_CLASS: String = "x-kotlin-value-class"

    /**
     * Names an operation's entry in the generated `Endpoints` object.
     *
     * Distinct from [NAME], which renames the *function*: the constant is derived from the verb and
     * the path rather than from the operationId, so the two can collide independently and each needs
     * its own way out.
     */
    const val ENDPOINT: String = "x-kotlin-endpoint"

    /** Not in our namespace: these are the spellings other toolchains already write. */
    const val DEPRECATED_REASON: String = "x-deprecated-reason"

    const val INTERNAL: String = "x-internal"

    const val NULLABLE: String = "x-nullable"

    const val ENUM_VARNAMES: String = "x-enum-varnames"

    /** NSwag's spelling of [ENUM_VARNAMES]. */
    const val ENUM_NAMES: String = "x-enumNames"

    const val ENUM_DESCRIPTIONS: String = "x-enum-descriptions"

    /** Every key in [KOTLIN_PREFIX] this generator implements. Adding a key means adding it here. */
    val kotlinKeys: Set<String> = setOf(NAME, SKIP, TYPE, VALUE_CLASS, ENDPOINT)
}

/**
 * The Kotlin name the document asks for, in place of the one this generator would derive.
 *
 * Validated as an identifier here rather than discovered as a compile error in generated source:
 * `x-kotlin-name: order id` would otherwise fail inside a file nobody wrote.
 */
internal fun Map<String, Any?>?.kotlinName(where: String): String? =
    extensionString(Ext.NAME, where)?.also { requireIdentifier(it, Ext.NAME, where) }

/**
 * `x-kotlin-endpoint` — this operation's name in the generated `Endpoints` object.
 *
 * The escape hatch for the one collision the document cannot fix by renaming: two operations whose
 * verb and path reduce to the same constant, which no `operationId` change can separate.
 */
internal fun Map<String, Any?>?.endpointConstant(where: String): String? =
    extensionString(Ext.ENDPOINT, where)?.also { requireIdentifier(it, Ext.ENDPOINT, where) }

/**
 * A name a document states has to be usable as written.
 *
 * Checked here rather than discovered as a compile error inside generated source, where the message
 * would point at a file nobody wrote.
 */
internal fun requireIdentifier(
    value: String,
    key: String,
    where: String,
) {
    if (IDENTIFIER.matches(value)) return
    throw OpenApiParseException(
        "$where: $key is '$value', which is not a legal Kotlin identifier. " +
            "Use letters, digits and underscores, starting with a letter or an underscore.",
    )
}

/**
 * A type the consumer already owns, in place of one generated from the schema.
 *
 * Validated as a qualified name here, because `ClassName.bestGuess` would otherwise fail deep
 * inside KotlinPoet with a message about neither the document nor the key.
 */
internal fun Map<String, Any?>?.externalType(where: String): String? {
    val value = extensionString(Ext.TYPE, where) ?: return null
    if (!QUALIFIED_NAME.matches(value)) {
        throw OpenApiParseException(
            "$where: ${Ext.TYPE} is '$value', which is not a qualified Kotlin type name. " +
                "Write it in full, as in 'com.example.money.Money'.",
        )
    }
    return value
}

/** Whether the document asks for this scalar to become a type of its own. */
internal fun Map<String, Any?>?.isValueClass(where: String): Boolean = extensionBoolean(Ext.VALUE_CLASS, where) == true

/** The text that goes inside `@Deprecated`, in place of this generator's boilerplate. */
internal fun Map<String, Any?>?.deprecatedReason(where: String): String? =
    extensionString(Ext.DEPRECATED_REASON, where)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Whether the document asks for this to be left out of the generated client.
 *
 * `x-internal` is the same request in the spelling Redocly, Bump and ReadMe already write, so a
 * document that hides an endpoint from its published reference hides it from the client too.
 */
internal fun Map<String, Any?>?.isExcluded(where: String): Boolean =
    extensionBoolean(Ext.SKIP, where) == true || extensionBoolean(Ext.INTERNAL, where) == true

/**
 * The entry names the document gives, in `enum` order.
 *
 * Two spellings mean the same thing, and a document carrying both with different values has said
 * two things — picking one would be a coin toss over what every generated entry is called.
 */
internal fun Map<String, Any?>?.enumEntryNames(where: String): List<String>? {
    val varnames = extensionStrings(Ext.ENUM_VARNAMES, where)
    val nswag = extensionStrings(Ext.ENUM_NAMES, where)
    if (varnames != null && nswag != null && varnames != nswag) {
        throw OpenApiParseException(
            "$where: ${Ext.ENUM_VARNAMES} and ${Ext.ENUM_NAMES} disagree ($varnames and $nswag). " +
                "They are two spellings of one thing; keep whichever is right and drop the other.",
        )
    }
    return varnames ?: nswag
}

internal fun Map<String, Any?>?.enumDescriptions(where: String): List<String>? = extensionStrings(Ext.ENUM_DESCRIPTIONS, where)

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

private val QUALIFIED_NAME = Regex("[a-zA-Z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

internal fun Map<String, Any?>?.extensionBoolean(
    key: String,
    where: String,
): Boolean? {
    val value = this?.get(key) ?: return null
    return value as? Boolean
        ?: throw OpenApiParseException("$where: $key must be true or false, but the document gives ${describe(value)}")
}

internal fun Map<String, Any?>?.extensionStrings(
    key: String,
    where: String,
): List<String>? {
    val value = this?.get(key) ?: return null
    val list =
        value as? List<*>
            ?: throw OpenApiParseException("$where: $key must be a list, but the document gives ${describe(value)}")
    return list.map { entry ->
        entry as? String
            ?: throw OpenApiParseException("$where: every entry of $key must be a string, but one is ${describe(entry)}")
    }
}
