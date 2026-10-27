package com.softistx.material.foundation.theme

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
import com.softistx.material.foundation.motion.SnapMotionScheme
import com.softistx.material.foundation.motion.StxMotion
import com.softistx.material.foundation.motion.systemReducesMotion
import com.softistx.material.foundation.style.colors

/**
 * The theme. It **wraps** Material 3 rather than replacing it, so a plain M3 component — or any
 * third-party M3 library — keeps working inside it. That is what makes this adoptable in an
 * application that already exists.
 *
 * Its signature is Material 3's own: a [ColorScheme], a [Typography] and [Shapes], each with a
 * default, plus an [StxMotion] for motion. A caller that already computes one of them — from a
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
 * that overshoots where the standard one settles. `motion = StxMotion(MotionScheme.standard())` calms
 * that for the whole tree, and every animation here follows — nothing holds its own curve.
 *
 * **[motion] is the one motion input**, and Material 3's scheme derives from it: a motion with
 * `enabled = false` installs a scheme whose every spec is a snap, any other installs its `scheme`.
 * **With no [motion] it follows the OS**: "Reduce motion" gives an `enabled = false` motion. Pass one
 * and the OS is not read at all. Desktop reads only `-Dstx.material.reduceMotion`;
 * `readDesktopReducedMotion()` is the app's opt-in to the OS.
 */
@Composable
fun StxTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = remember(isDark) { stxColorScheme(DefaultSeed, isDark) },
    colors: StxColors = remember(colorScheme, isDark) { stxColors(colorScheme, isDark) },
    spacing: StxSpacing = StxSpacing(),
    motion: StxMotion? = null,
    typography: Typography = MaterialTheme.typography,
    shapes: Shapes = MaterialTheme.shapes,
    content: @Composable () -> Unit,
) {
    val reduced = motion == null && systemReducesMotion()
    val stxMotion = motion ?: remember(reduced) { StxMotion.system(reduced) }
    val scheme = if (stxMotion.enabled) stxMotion.scheme else SnapMotionScheme
    CompositionLocalProvider(
        LocalStrangeColors provides colors,
        LocalStrangeSpacing provides spacing,
        LocalStrangeMotion provides stxMotion,
        LocalStrangeShapes provides shapes,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = scheme,
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
