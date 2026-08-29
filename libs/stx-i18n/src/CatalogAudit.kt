package com.strange.i18n

import java.util.Locale

/**
 * What each catalog is missing, and what it has that nothing else does.
 *
 * Translation drift is the standing maintenance problem of every catalog and nothing detects it on
 * its own: a key added in English ships, works, and is simply absent in French until somebody
 * happens to read that screen in French. The project this module was ported from had drifted to 55
 * English keys against 62 French ones without anything noticing.
 *
 * It is cheap to detect, so this exists to make it a one-line spec:
 *
 * ```kotlin
 * scenario("every language is complete") { messages.audit().isClean shouldBe true }
 * ```
 */
data class CatalogAudit(
    val fallback: Locale,
    val reference: Set<String>,
    val locales: List<LocaleAudit>,
) {
    /** Whether every catalog answers everything the fallback does, and nothing it should not. */
    val isClean: Boolean get() = locales.all { it.isClean }

    /** A report a failing spec can print and act on. */
    override fun toString(): String =
        when {
            isClean -> {
                "every catalog matches ${fallback.toLanguageTag()} (${reference.size} keys)"
            }

            else -> {
                buildString {
                    append("against ${fallback.toLanguageTag()} (${reference.size} keys):")
                    locales.filterNot { it.isClean }.forEach { append("\n").append(it) }
                }
            }
        }
}

/** One locale's standing against the fallback. */
data class LocaleAudit(
    val locale: Locale,
    val missing: Set<String>,
    val extra: Set<String>,
) {
    val isClean: Boolean get() = missing.isEmpty() && extra.isEmpty()

    override fun toString(): String =
        buildString {
            append("  ").append(locale.toLanguageTag()).append(':')
            if (missing.isNotEmpty()) append("\n    missing ").append(missing.sorted().joinToString(", "))
            if (extra.isNotEmpty()) append("\n    only here ").append(extra.sorted().joinToString(", "))
        }
}
