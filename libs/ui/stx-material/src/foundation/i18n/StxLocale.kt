package com.softistx.material.foundation.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale

/**
 * The catalogue for a language tag. Only the language subtag counts, case-insensitively, so `fr`,
 * `FR` and `fr-CA` all give [StxStrings.Fr]. An unknown or empty tag gives [StxStrings.En].
 */
fun StxStrings.Companion.forLanguage(tag: String): StxStrings =
    when (
        tag
            .substringBefore('-')
            .substringBefore('_')
            .trim()
            .lowercase()
    ) {
        "fr" -> Fr
        else -> En
    }

/** The catalogue for the device's current locale, re-resolved when the locale changes. */
@Composable
fun rememberStxStrings(): StxStrings {
    val language = Locale.current.language
    return remember(language) { StxStrings.forLanguage(language) }
}
