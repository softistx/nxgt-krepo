package com.softistx.r2jdbc.sql

import com.softistx.r2jdbc.Backend

/**
 * Rewrites the `?` a caller writes into the `$1`, `$2` … Postgres counts, and leaves MySQL alone.
 *
 * **The caller writes `?` on both servers, and that is the whole point of this file.** Nothing
 * between a hand-written statement and the driver rewrites one spelling into the other — measured
 * both ways, on both servers, in `DriverContractTest`:
 *
 * | written | Postgres | MySQL |
 * | --- | --- | --- |
 * | `?` | `syntax error at or near "as"` | works |
 * | `$1` | works | `Unknown column '$1' in 'field list'` |
 *
 * `stx-jpa` has the matching wound and no cure: `@SQLDelete` is handed to the driver untouched, so
 * the same annotation cannot be written once for both — `LegacyNote` and `LegacyDollarNote` exist to
 * pin exactly that. Owning the placeholder is the first thing a SQL library owes its caller.
 *
 * **A `?` inside a literal is not a parameter**, which is why this is a scanner and not a
 * `replace`. It steps over single-quoted strings (including `''` and, after an `E`, a backslash
 * escape), double-quoted identifiers, `$tag$` dollar-quoted bodies, `--` line comments and nested
 * block comments. Postgres also spells three `jsonb` operators with a `?`; write `??` for a
 * literal one, the same escape the Postgres JDBC driver uses.
 */
internal fun String.numberedPlaceholders(backend: Backend): String {
    if (!backend.numbered) return this
    if ('?' !in this) return this

    val out = StringBuilder(length + PADDING)
    var index = 0
    var position = 0

    while (position < length) {
        val c = this[position]
        when {
            c == '\'' -> {
                position = copyQuoted(position, '\'', out)
            }

            c == '"' -> {
                position = copyQuoted(position, '"', out)
            }

            c == '-' && peek(position + 1) == '-' -> {
                position = copyLineComment(position, out)
            }

            c == '/' && peek(position + 1) == '*' -> {
                position = copyBlockComment(position, out)
            }

            c == '$' && dollarTag(position) != null -> {
                position = copyDollarQuoted(position, out)
            }

            c == '?' && peek(position + 1) == '?' -> {
                out.append('?')
                position += 2
            }

            c == '?' -> {
                out.append('$').append(++index)
                position++
            }

            else -> {
                out.append(c)
                position++
            }
        }
    }
    return out.toString()
}

private const val PADDING = 8

private fun String.peek(at: Int): Char? = getOrNull(at)

/** Copies a `'…'` or `"…"` run, treating a doubled delimiter as an escaped one. */
private fun String.copyQuoted(
    start: Int,
    delimiter: Char,
    out: StringBuilder,
): Int {
    // `E'…'` and `e'…'` take backslash escapes; every other single-quoted string in Postgres does
    // not, and a lone backslash in one is just a backslash.
    val escaping = delimiter == '\'' && start > 0 && (this[start - 1] == 'E' || this[start - 1] == 'e')
    out.append(delimiter)
    var position = start + 1
    while (position < length) {
        val c = this[position]
        out.append(c)
        position++
        when {
            escaping && c == '\\' && position < length -> out.append(this[position++])
            c == delimiter && peek(position) == delimiter -> out.append(this[position++])
            c == delimiter -> return position
        }
    }
    return position
}

private fun String.copyLineComment(
    start: Int,
    out: StringBuilder,
): Int {
    val end = indexOf('\n', start).let { if (it == -1) length else it }
    out.append(this, start, end)
    return end
}

/** Copies a block comment. Postgres nests them, so this counts depth rather than finding the first close. */
private fun String.copyBlockComment(
    start: Int,
    out: StringBuilder,
): Int {
    var depth = 0
    var position = start
    while (position < length) {
        when {
            this[position] == '/' && peek(position + 1) == '*' -> {
                depth++
                out.append("/*")
                position += 2
            }

            this[position] == '*' && peek(position + 1) == '/' -> {
                depth--
                out.append("*/")
                position += 2
                if (depth == 0) return position
            }

            else -> {
                out.append(this[position++])
            }
        }
    }
    return position
}

/** The `$tag$` opening a dollar-quoted body at [start], or null when this `$` opens nothing. */
private fun String.dollarTag(start: Int): String? {
    val close = indexOf('$', start + 1)
    if (close == -1) return null
    val tag = substring(start + 1, close)
    // A tag may not start with a digit — which is what keeps `select $1$2`, two Postgres parameters
    // a caller wrote by hand, from being read as an empty dollar-quoted body.
    val named = tag.first().let { it.isLetter() || it == '_' } && tag.all { it.isLetterOrDigit() || it == '_' }
    return if (tag.isEmpty() || named) substring(start, close + 1) else null
}

private fun String.copyDollarQuoted(
    start: Int,
    out: StringBuilder,
): Int {
    val tag = dollarTag(start) ?: return start
    val end = indexOf(tag, start + tag.length).let { if (it == -1) length else it + tag.length }
    out.append(this, start, end)
    return end
}
