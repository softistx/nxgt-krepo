package com.softistx.material.theme

import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import com.softistx.material.motion.StrangeMotion

/**
 * Where the tokens live during composition.
 *
 * There are only three, and each is here because Material 3 has nothing to read instead: a spacing
 * scale, the semantic colour roles, and the motion helpers. Everything M3 already names — the
 * colour scheme, the type scale, the shape ladder, elevation — is read from `MaterialTheme`, not
 * mirrored into a second vocabulary that can drift from it.
 *
 * All are `static`: a theme changes rarely and wholesale, so invalidating every reader is cheaper
 * than tracking each one. The defaults exist so a component still renders sensibly when someone
 * previews it outside a [StrangeTheme] — a missing theme should look plain, not crash.
 */
val LocalStrangeSpacing = staticCompositionLocalOf { StrangeSpacing() }

val LocalStrangeMotion = staticCompositionLocalOf { StrangeMotion() }

val LocalStrangeColors =
    staticCompositionLocalOf { strangeColors(seed = DefaultSeed, isDark = false) }

/**
 * Material 3's own [Shapes], mirrored so a `Style` can read them.
 *
 * This is a mirror, not a second ladder: it holds the very instance [StrangeTheme] hands
 * `MaterialExpressiveTheme`, so the two cannot disagree. It exists only because
 * `MaterialTheme.localMaterialTheme` looks public in the bytecode but is `internal` to Kotlin, so
 * nothing outside `material3` can reach M3's tokens without a composable — and a `Style` block is
 * not one. A composable reads `MaterialTheme.shapes` directly and never comes here.
 */
val LocalStrangeShapes = staticCompositionLocalOf { Shapes() }
