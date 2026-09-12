package com.softistx.material.theme

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
import com.softistx.material.motion.StxMotion

/**
 * The theme. It **wraps** Material 3 rather than replacing it, so a plain M3 component — or any
 * third-party M3 library — keeps working inside it. That is what makes this adoptable in an
 * application that already exists.
 *
 * Its signature is Material 3's own: a [ColorScheme], a [Typography], [Shapes] and a
 * [MotionScheme], each with a default. A caller that already computes one of the four — from a
 * wallpaper, from a brand kit, from a designer's export — passes it and keeps everything else.
 * `StxThemeProvider` is the shorthand for the common case, and the only thing that knows the
 * scheme comes from the wallpaper on Android and from a seed everywhere else.
 *
 * **Nothing here re-describes what M3 already names.** Shapes are M3's [Shapes], with all eight
 * slots; elevation is whatever a component's own `*Defaults` gives it; colour is the [ColorScheme].
 * The two extras are the two M3 does not have: a spacing scale, and the semantic colour roles
 * (`success`, `info`, `warning`) that M3 leaves to the product.
 *
 * It installs `MaterialExpressiveTheme`: rounder shapes, springier motion, and a `MotionScheme`
 * that overshoots where the standard one settles. `motionScheme = MotionScheme.standard()` turns
 * that off for the whole tree, and every animation here follows — nothing holds its own curve.
 */
@Composable
fun StxTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = remember(isDark) { stxColorScheme(DefaultSeed, isDark) },
    colors: StxColors = remember(colorScheme, isDark) { stxColors(colorScheme, isDark) },
    spacing: StxSpacing = StxSpacing(),
    motionScheme: MotionScheme = MotionScheme.expressive(),
    motion: StxMotion = remember(motionScheme) { StxMotion(motionScheme) },
    typography: Typography = MaterialTheme.typography,
    shapes: Shapes = MaterialTheme.shapes,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStrangeColors provides colors,
        LocalStrangeSpacing provides spacing,
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
 * The tokens Material 3 does not have, reached the way `MaterialTheme.colorScheme` is.
 *
 * There is deliberately no `StxTheme.typography`, `.shapes` or `.elevation` shadowing M3 —
 * a component asks `MaterialTheme` for those, and there is only ever one answer.
 */
object StxTheme {
    val colors: StxColors
        @Composable @ReadOnlyComposable
        get() = LocalStrangeColors.current

    val spacing: StxSpacing
        @Composable @ReadOnlyComposable
        get() = LocalStrangeSpacing.current

    val motion: StxMotion
        @Composable @ReadOnlyComposable
        get() = LocalStrangeMotion.current
}
