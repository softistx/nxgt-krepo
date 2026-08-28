package com.strange.material.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.strange.material.motion.StrangeMotion

/**
 * Where the tokens live during composition.
 *
 * All five are `static`: a theme changes rarely and wholesale, so invalidating every reader is
 * cheaper than tracking each one. The defaults exist so a component still renders sensibly when
 * someone previews it outside a [StrangeTheme] — a missing theme should look plain, not crash.
 */
val LocalStrangeSpacing = staticCompositionLocalOf { StrangeSpacing() }

val LocalStrangeRadii = staticCompositionLocalOf { StrangeRadii() }

val LocalStrangeElevation = staticCompositionLocalOf { StrangeElevation() }

val LocalStrangeMotion = staticCompositionLocalOf { StrangeMotion() }

val LocalStrangeColors =
    staticCompositionLocalOf { strangeColors(seed = DefaultSeed, isDark = false) }

/**
 * Material 3's own `Shapes`, mirrored so a `Style` can read them.
 *
 * `MaterialTheme.localMaterialTheme` looks public in the bytecode but is `internal` to Kotlin, so
 * nothing outside `material3` can reach M3's tokens without a composable — and a `Style` block is
 * not one. [StrangeTheme] provides the same instance here that it hands `MaterialExpressiveTheme`,
 * so the mirror cannot drift from the original.
 */
val LocalStrangeShapes = staticCompositionLocalOf { StrangeRadii().toShapes() }
