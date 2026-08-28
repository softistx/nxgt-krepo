package com.strange.material.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.strange.material.motion.StrangeMotion

/**
 * The theme every component here reads from.
 *
 * It *wraps* [MaterialTheme] rather than replacing it. That is the decision the whole library
 * rests on: an application already using Material 3, or any third-party M3 component, keeps
 * working unchanged inside a `StrangeTheme` — the colour scheme, type scale and shapes are
 * installed where M3 looks for them, and the extra tokens ride alongside on their own locals.
 * A design system that refused to do this would force an all-or-nothing adoption.
 *
 * The usual call names nothing at all:
 *
 * ```kotlin
 * StrangeTheme { App() }
 * ```
 *
 * and a product that has a brand colour names one thing:
 *
 * ```kotlin
 * StrangeTheme(seed = Color(0xFF7C3AED)) { App() }
 * ```
 */
@Composable
fun StrangeTheme(
    seed: Color = DefaultSeed,
    isDark: Boolean = false,
    colors: StrangeColors = remember(seed, isDark) { strangeColors(seed, isDark) },
    spacing: StrangeSpacing = StrangeSpacing(),
    radii: StrangeRadii = StrangeRadii(),
    elevation: StrangeElevation = StrangeElevation(),
    motion: StrangeMotion = StrangeMotion(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStrangeColors provides colors,
        LocalStrangeSpacing provides spacing,
        LocalStrangeRadii provides radii,
        LocalStrangeElevation provides elevation,
        LocalStrangeMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colors.scheme,
            shapes = remember(radii) { radii.toShapes() },
            content = content,
        )
    }
}

/**
 * The tokens, reached the way Material 3's own are — `StrangeTheme.spacing.md`.
 *
 * Same shape as `MaterialTheme.colorScheme`, so a caller who knows one knows the other.
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
