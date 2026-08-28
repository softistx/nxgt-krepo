package com.strange.material.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/** The hues the three extra semantic roles are grown from, when a caller names none. */
private val SuccessSeed = Color(0xFF16A34A)
private val InfoSeed = Color(0xFF2563EB)
private val WarningSeed = Color(0xFFF59E0B)

/**
 * A whole palette from one seed colour.
 *
 * The M3 roles come from material-kolor, which is the same tonal-palette algorithm Android uses,
 * so a caller names one brand colour and gets 48 roles that are correct in both light and dark.
 *
 * The three extra roles are derived the same way rather than hard-coded: each is the *primary* of
 * a scheme seeded with its own hue. That is what makes them behave like real roles — `onSuccess`
 * is guaranteed to be readable on `success`, and the pair flips correctly in dark mode, because
 * the same algorithm that guarantees it for `primary` produced it. A hard-coded green would be
 * right in light mode and wrong in dark.
 */
fun strangeColors(
    seed: Color,
    isDark: Boolean,
    success: Color = SuccessSeed,
    info: Color = InfoSeed,
    warning: Color = WarningSeed,
): StrangeColors {
    val scheme = dynamicColorScheme(seedColor = seed, isDark = isDark)
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

/** The default brand seed, used when a caller installs the theme without naming one. */
val DefaultSeed: Color = Color(0xFF5B5BD6)

/** The M3 scheme alone, for a caller that wants to hand [StrangeTheme] a scheme it already has. */
fun strangeColors(
    scheme: ColorScheme,
    isDark: Boolean,
): StrangeColors = strangeColors(seed = scheme.primary, isDark = isDark).copy(scheme = scheme)
