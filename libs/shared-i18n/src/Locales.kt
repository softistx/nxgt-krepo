package com.strange.i18n

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
