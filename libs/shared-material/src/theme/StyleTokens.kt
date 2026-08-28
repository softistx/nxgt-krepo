package com.strange.material.theme

import androidx.compose.foundation.style.StyleScope
import androidx.compose.material3.ColorScheme
import com.strange.material.motion.StrangeMotion

/**
 * The theme's tokens, reachable from inside a `Style { }`.
 *
 * A [StyleScope] is not a composable, so `StrangeTheme.spacing` does not work there. It *is* a
 * `CompositionLocalAccessorScope`, which is the seam: `currentValue` reads a local at the point
 * the style is resolved. These four extensions are what let a style be written in the same
 * vocabulary as the rest of the library —
 *
 * ```kotlin
 * Style {
 *     background(colors.scheme.primary)
 *     shape(RoundedCornerShape(radii.full))
 *     contentPaddingHorizontal(spacing.md)
 * }
 * ```
 *
 * — instead of closing over values captured somewhere else, which would freeze a style to the
 * theme that happened to be in scope when it was constructed.
 */
val StyleScope.colors: StrangeColors
    get() = LocalStrangeColors.currentValue

val StyleScope.scheme: ColorScheme
    get() = LocalStrangeColors.currentValue.scheme

val StyleScope.spacing: StrangeSpacing
    get() = LocalStrangeSpacing.currentValue

val StyleScope.radii: StrangeRadii
    get() = LocalStrangeRadii.currentValue

val StyleScope.elevation: StrangeElevation
    get() = LocalStrangeElevation.currentValue

val StyleScope.motion: StrangeMotion
    get() = LocalStrangeMotion.currentValue
