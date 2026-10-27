package com.softistx.material.foundation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.softistx.material.foundation.style.scheme

/** The hues the extra semantic roles are grown from, when a caller names none. `info` has none: it is the scheme's `secondary`. */
private val SuccessSeed = Color(0xFF16A34A)
private val WarningSeed = Color(0xFFF59E0B)

/** The default brand seed, used when a caller installs the theme without naming one. */
val DefaultSeed: Color = Color(0xFF5B5BD6)

/**
 * A Material 3 [ColorScheme] from one seed colour, through material-kolor — the same tonal-palette
 * algorithm Android uses, so a caller names one brand colour and gets 48 roles that are correct in
 * both light and dark.
 *
 * This is the one place a seed becomes a scheme. It is separate from [stxColors] on purpose:
 * where the scheme comes from is a platform decision — the wallpaper on Android, a seed everywhere
 * else — and the semantic roles have to be added to whichever one arrives.
 *
 * The default is calm: [PaletteStyle.TonalSpot] on the 2025 Material colour spec, so one accent
 * (the primary) stands out and the rest of the palette stays quiet. [PaletteStyle.Vibrant] and
 * [PaletteStyle.Expressive] are one argument away for a brand that wants more colour.
 *
 * @param paletteStyle how the seed is spread across the tonal palettes
 * @param specVersion which revision of the Material colour spec computes the roles
 */
fun stxColorScheme(
    seed: Color,
    isDark: Boolean,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
): ColorScheme =
    dynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        style = paletteStyle,
        specVersion = specVersion,
    )

/**
 * The semantic roles Material 3 does not define, added to whatever scheme it is given.
 *
 * The three extra roles are derived rather than hard-coded: each is the *primary* of a scheme
 * seeded with its own hue. That is what makes them behave like real roles — `onSuccess` is
 * guaranteed readable on `success`, and the pair flips correctly in dark mode, because the same
 * algorithm that guarantees it for `primary` produced it. A hard-coded green would be right in
 * light mode and wrong in dark.
 *
 * `info` is the exception: left at `null` it is the scheme's own `secondary` roles, so a calm
 * palette has no fourth hue sitting close to the primary. Name a colour to grow a dedicated role.
 *
 * `error` is not among them: it is delegated to [scheme] by [StxColors], because a design
 * system does not want two reds.
 */
fun stxColors(
    scheme: ColorScheme,
    isDark: Boolean,
    success: Color = SuccessSeed,
    info: Color? = null,
    warning: Color = WarningSeed,
): StxColors {
    val successRole = roleFrom(success, isDark)
    val infoRole =
        if (info == null) {
            ToneColors(
                main = scheme.secondary,
                onMain = scheme.onSecondary,
                container = scheme.secondaryContainer,
                onContainer = scheme.onSecondaryContainer,
            )
        } else {
            roleFrom(info, isDark)
        }
    val warningRole = roleFrom(warning, isDark)
    return StxColors(
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
fun stxColors(
    seed: Color,
    isDark: Boolean,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
): StxColors = stxColors(scheme = stxColorScheme(seed, isDark, paletteStyle, specVersion), isDark = isDark)

/** One semantic role, taken as the primary of a scheme grown from [hue]. */
private fun roleFrom(
    hue: Color,
    isDark: Boolean,
): ToneColors =
    dynamicColorScheme(seedColor = hue, isDark = isDark, specVersion = ColorSpec.SpecVersion.SPEC_2025).let {
        ToneColors(
            main = it.primary,
            onMain = it.onPrimary,
            container = it.primaryContainer,
            onContainer = it.onPrimaryContainer,
        )
    }
