package com.softistx.graphix.message

/**
 * Every key the built-in scalars look up. A [GraphixMessages] of your own answers these; anything
 * it does not answer falls back to whatever that source does with an unknown key.
 *
 * The keys are **generic on purpose**. `{scalar}` carries which scalar failed, so a new scalar
 * needs no new key and no new translation — the catalogues stop growing at the point where the
 * scalars start.
 *
 * | Argument | What it holds |
 * | --- | --- |
 * | `scalar` | the GraphQL scalar's name, `Instant` |
 * | `actual` | the Kotlin type that arrived, or the kind of literal |
 * | `expected` | the kind of literal the scalar wanted, already translated |
 * | `value` | the text that could not be parsed |
 * | `reason` | the parser's own message |
 * | `constraint` | the range that was not met, already translated |
 */
object MessageKeys {
    /** A resolver returned a type this scalar cannot put on the wire. `scalar`, `actual`. */
    const val SERIALIZE = "stx.graphix.messages.scalar.serialize"

    /** A variable's value is of a type this scalar cannot read. `scalar`, `actual`. */
    const val PARSE_VALUE = "stx.graphix.messages.scalar.parseValue"

    /** A literal in the document is of the wrong kind. `scalar`, `expected`, `actual`. */
    const val PARSE_LITERAL = "stx.graphix.messages.scalar.parseLiteral"

    /** The right kind, the wrong content. `scalar`, `value`. */
    const val PARSE = "stx.graphix.messages.scalar.parse"

    /** [PARSE] with the underlying parser's message. `scalar`, `value`, `reason`. */
    const val PARSE_REASON = "stx.graphix.messages.scalar.parseReason"

    /** A number outside the scalar's range. `scalar`, `constraint`, `value`. */
    const val RANGE = "stx.graphix.messages.scalar.range"

    /** Names of literal kinds, used as `expected` and `actual` in [PARSE_LITERAL]. */
    const val LITERAL_STRING = "stx.graphix.messages.literal.string"

    const val LITERAL_INT = "stx.graphix.messages.literal.int"

    const val LITERAL_FLOAT = "stx.graphix.messages.literal.float"

    const val LITERAL_BOOLEAN = "stx.graphix.messages.literal.boolean"

    const val LITERAL_ENUM = "stx.graphix.messages.literal.enum"

    const val LITERAL_OBJECT = "stx.graphix.messages.literal.object"

    const val LITERAL_LIST = "stx.graphix.messages.literal.list"

    const val LITERAL_NULL = "stx.graphix.messages.literal.null"

    /** Names of ranges, used as `constraint` in [RANGE]. */
    const val RANGE_POSITIVE = "stx.graphix.messages.range.positive"

    const val RANGE_NEGATIVE = "stx.graphix.messages.range.negative"

    const val RANGE_NON_POSITIVE = "stx.graphix.messages.range.nonPositive"

    const val RANGE_NON_NEGATIVE = "stx.graphix.messages.range.nonNegative"

    /** The 64-bit / 32-bit / 16-bit / 8-bit window an integral scalar accepts. `min`, `max`. */
    const val RANGE_BETWEEN = "stx.graphix.messages.range.between"

    /** Every key above. A catalogue that answers all of these is complete. */
    val All: List<String> =
        listOf(
            SERIALIZE,
            PARSE_VALUE,
            PARSE_LITERAL,
            PARSE,
            PARSE_REASON,
            RANGE,
            LITERAL_STRING,
            LITERAL_INT,
            LITERAL_FLOAT,
            LITERAL_BOOLEAN,
            LITERAL_ENUM,
            LITERAL_OBJECT,
            LITERAL_LIST,
            LITERAL_NULL,
            RANGE_POSITIVE,
            RANGE_NEGATIVE,
            RANGE_NON_POSITIVE,
            RANGE_NON_NEGATIVE,
            RANGE_BETWEEN,
        )
}
