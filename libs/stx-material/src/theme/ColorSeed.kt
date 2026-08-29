package com.strange.material.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/** The hues the three extra semantic roles are grown from, when a caller names none. */
private val SuccessSeed = Color(0xFF16A34A)
private val InfoSeed = Color(0xFF2563EB)
private val WarningSeed = Color(0xFFF59E0B)

/** The default brand seed, used when a caller installs the theme without naming one. */
val DefaultSeed: Color = Color(0xFF5B5BD6)

/**
 * A Material 3 [ColorScheme] from one seed colour, through material-kolor — the same tonal-palette
 * algorithm Android uses, so a caller names one brand colour and gets 48 roles that are correct in
 * both light and dark.
 *
 * This is the one place a seed becomes a scheme. It is separate from [strangeColors] on purpose:
 * where the scheme comes from is a platform decision — the wallpaper on Android, a seed everywhere
 * else — and the semantic roles have to be added to whichever one arrives.
 */
fun strangeColorScheme(
    seed: Color,
    isDark: Boolean,
): ColorScheme = dynamicColorScheme(seedColor = seed, isDark = isDark)

/**
 * The semantic roles Material 3 does not define, added to whatever scheme it is given.
 *
 * The three extra roles are derived rather than hard-coded: each is the *primary* of a scheme
 * seeded with its own hue. That is what makes them behave like real roles — `onSuccess` is
 * guaranteed readable on `success`, and the pair flips correctly in dark mode, because the same
 * algorithm that guarantees it for `primary` produced it. A hard-coded green would be right in
 * light mode and wrong in dark.
 *
 * `error` is not among them: it is delegated to [scheme] by [StrangeColors], because a design
 * system does not want two reds.
 */
fun strangeColors(
    scheme: ColorScheme,
    isDark: Boolean,
    success: Color = SuccessSeed,
    info: Color = InfoSeed,
    warning: Color = WarningSeed,
): StrangeColors {
    val successRole = roleFrom(success, isDark)
    val infoRole = roleFrom(info, isDark)
    val warningRole = roleFrom(warning, isDark)
    return StrangeColors(
        scheme = scheme,
        success = successRole.main,
        onSuccess = successRole.onMain,
        successContainer = successRole.container,
        onSuccessContainer = successRole.onContainer,
        info = infoRole.main,
        onInfo = infoRole.onMain,
        infoContainer = infoRole.container,
        onInfoContainer = infoRole.onContainer,
        warning = warningRole.main,
        onWarning = warningRole.onMain,
        warningContainer = warningRole.container,
        onWarningContainer = warningRole.onContainer,
    )
}

/** The seed path in one call, for a caller with a brand colour and no scheme of its own. */
fun strangeColors(
    seed: Color,
    isDark: Boolean,
): StrangeColors = strangeColors(scheme = strangeColorScheme(seed, isDark), isDark = isDark)

/** One semantic role, taken as the primary of a scheme grown from [hue]. */
private fun roleFrom(
    hue: Color,
    isDark: Boolean,
): ToneColors =
    dynamicColorScheme(seedColor = hue, isDark = isDark).let {
        ToneColors(
            main = it.primary,
            onMain = it.onPrimary,
            container = it.primaryContainer,
            onContainer = it.onPrimaryContainer,
        )
    }
