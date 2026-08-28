package com.strange.material.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.strange.material.motion.StrangeMotion

/**
 * The theme. It **wraps** Material 3 rather than replacing it, so a plain M3 component — or any
 * third-party M3 library — keeps working inside it. That is what makes this adoptable in an
 * application that already exists.
 *
 * Its signature is Material 3's own: a [ColorScheme], a [Typography], [Shapes] and a
 * [MotionScheme], each with a default. A caller that already computes one of the four — from a
 * wallpaper, from a brand kit, from a designer's export — passes it and keeps everything else.
 * `StrangeThemeProvider` is the shorthand for the common case, and the only thing that knows the
 * scheme comes from the wallpaper on Android and from a seed everywhere else.
 *
 * It installs `MaterialExpressiveTheme`: rounder shapes, springier motion, and a `MotionScheme`
 * that overshoots where the standard one settles. `motionScheme = MotionScheme.standard()` turns
 * that off for the whole tree, and every animation here follows — nothing holds its own curve.
 */
@Composable
fun StrangeTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = remember(isDark) { strangeColorScheme(DefaultSeed, isDark) },
    colors: StrangeColors = remember(colorScheme, isDark) { strangeColors(colorScheme, isDark) },
    spacing: StrangeSpacing = StrangeSpacing(),
    radii: StrangeRadii = StrangeRadii(),
    elevation: StrangeElevation = StrangeElevation(),
    motionScheme: MotionScheme = MotionScheme.expressive(),
    motion: StrangeMotion = remember(motionScheme) { StrangeMotion(motionScheme) },
    typography: Typography = MaterialTheme.typography,
    shapes: Shapes = remember(radii) { radii.toShapes() },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStrangeColors provides colors,
        LocalStrangeSpacing provides spacing,
        LocalStrangeRadii provides radii,
        LocalStrangeElevation provides elevation,
        LocalStrangeMotion provides motion,
        LocalStrangeShapes provides shapes,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}

/**
 * The tokens, reached the way `MaterialTheme.colorScheme` is. Everything M3 already names is read
 * through `MaterialTheme` itself — there is no `StrangeTheme.typography` shadowing it.
 */
object StrangeTheme {
    val colors: StrangeColors
        @Composable @ReadOnlyComposable
        get() = LocalStrangeColors.current

    val spacing: StrangeSpacing
        @Composable @ReadOnlyComposable
        get() = LocalStrangeSpacing.current

    val radii: StrangeRadii
        @Composable @ReadOnlyComposable
        get() = LocalStrangeRadii.current

    val elevation: StrangeElevation
        @Composable @ReadOnlyComposable
        get() = LocalStrangeElevation.current

    val motion: StrangeMotion
        @Composable @ReadOnlyComposable
        get() = LocalStrangeMotion.current
}
