package com.softistx.material.foundation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * The theme as an application wants it: name a brand colour, and get the platform's best answer.
 *
 * This is the one-line case [StxTheme] deliberately does not assume. It resolves the colour
 * scheme through [platformColorScheme] — the wallpaper on Android 12+, the seed everywhere else —
 * and hands everything else to [StxTheme] untouched. Anything more specific than this (a
 * scheme from a brand kit, a different `MotionScheme`, custom shapes) calls [StxTheme]
 * directly; there is nothing this shorthand can express that that one cannot.
 */
@Composable
fun StxThemeProvider(
    seed: Color = DefaultSeed,
    isDark: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    motionScheme: MotionScheme = MotionScheme.expressive(),
    content: @Composable () -> Unit,
) {
    StxTheme(
        isDark = isDark,
        colorScheme = platformColorScheme(
                seed = seed,
                isDark = isDark,
                dynamicColor = dynamicColor,
                paletteStyle = paletteStyle,
                specVersion = specVersion,
            ),
        motionScheme = motionScheme,
        content = content,
    )
}
