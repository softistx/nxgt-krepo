package com.softistx.graphix.http

import java.util.Locale

/**
 * The client's preferred locale from an `Accept-Language` header, or `null` when it did not say.
 * `null` and not a default: the caller decides what "did not say" means, and here it means
 * graphql-java's own default rather than a language this library picked.
 *
 * The header comes from a client nobody controls, so a malformed one is a `null` and never a
 * failed request. Quality values are honoured — `Locale.LanguageRange.parse` sorts by weight —
 * and the first tag that is actually a tag wins. Which locales are *available* is a question for
 * the message source, which walks `fr-CA` → `fr` → base per key.
 */
fun acceptedLocale(header: String?): Locale? {
    if (header.isNullOrBlank()) return null
    return try {
        Locale.LanguageRange
            .parse(header)
            .asSequence()
            .map { Locale.forLanguageTag(it.range) }
            .firstOrNull { it.toLanguageTag() != "und" }
    } catch (_: IllegalArgumentException) {
        null
    }
}
