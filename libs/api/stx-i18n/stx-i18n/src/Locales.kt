package com.softistx.i18n

import java.util.Locale

/**
 * The locales to consult for this one, most specific first: `fr-CA` → `fr`.
 *
 * A catalog is written per language and refined per region, so `fr-CA` is a handful of overrides
 * over `fr` rather than a copy of it. Walking the chain per *key* is what lets it be — the whole
 * point being that a key `fr-CA` does not override still answers in French.
 *
 * [Locale.ROOT] is deliberately not in here: it is the catalog of last resort and belongs after the
 * fallback locale, not after every locale's own parents.
 */
fun Locale.chain(): List<Locale> =
    buildList {
        add(this@chain)
        if (variant.isNotEmpty()) add(Locale.of(language, country))
        if (country.isNotEmpty() && language.isNotEmpty()) add(Locale.of(language))
    }.distinct().filter { it.language.isNotEmpty() }

/**
 * The best of [available] for an `Accept-Language` header, or [fallback] when none of them fit.
 *
 * ```
 * fr-CA,fr;q=0.9,en;q=0.8   →   fr-CA if it is shipped, else fr, else en
 * ```
 *
 * This is the half of i18n a server needs and an application with a settings screen does not: the
 * locale is not a preference stored somewhere, it is a ranked list arriving on every request, and
 * honouring the ranking is the difference between a Québécois reader getting French and getting
 * whatever happened to be first in the list.
 *
 * `Locale.lookup` is RFC 4647 lookup: it takes the highest-weighted range that matches and
 * truncates towards the language when the region is not shipped. A **malformed header falls back
 * rather than throwing** — it is user input, and no request should fail over one.
 */
fun negotiate(
    acceptLanguage: String?,
    available: Collection<Locale>,
    fallback: Locale,
): Locale {
    if (acceptLanguage.isNullOrBlank()) return fallback

    val ranges =
        try {
            Locale.LanguageRange.parse(acceptLanguage)
        } catch (_: IllegalArgumentException) {
            return fallback
        }

    return Locale.lookup(ranges, available) ?: fallback
}
