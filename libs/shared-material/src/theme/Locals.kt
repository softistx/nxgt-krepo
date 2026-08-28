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
