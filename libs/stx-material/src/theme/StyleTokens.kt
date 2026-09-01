package com.softistx.material.theme

import androidx.compose.foundation.style.StyleScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import com.softistx.material.motion.StxMotion

/**
 * The tokens, reachable from inside a `Style { }`.
 *
 * A [StyleScope] is not a composable, so `MaterialTheme.shapes` and `StxTheme.spacing` do not
 * work there. It *is* a `CompositionLocalAccessorScope`, which is the seam: `currentValue` reads a
 * local at the point the style is resolved rather than closing over whatever was in scope when the
 * style was written.
 *
 * ```kotlin
 * Style {
 *     background(scheme.surfaceContainerHigh)     // Material 3's
 *     shape(shapes.medium)                        // Material 3's
 *     contentPaddingHorizontal(spacing.md)        // ours — M3 has no spacing scale
 * }
 * ```
 *
 * [scheme] and [shapes] are Material 3's own types holding the values `StxTheme` installed —
 * the *same* instances it hands `MaterialExpressiveTheme`, so the two cannot drift.
 *
 * They have to be mirrored rather than read from M3 directly: `MaterialTheme.localMaterialTheme`
 * looks public in the bytecode but is `internal` to Kotlin, so nothing outside `material3` can
 * reach its tokens without a composable. That is a real limit of building a design system on M3,
 * not an oversight here.
 */
val StyleScope.scheme: ColorScheme
    get() = LocalStrangeColors.currentValue.scheme

val StyleScope.shapes: Shapes
    get() = LocalStrangeShapes.currentValue

/** The semantic roles Material 3 does not define — `success`, `info`, `warning`, and `tone(…)`. */
val StyleScope.colors: StxColors
    get() = LocalStrangeColors.currentValue

val StyleScope.spacing: StxSpacing
    get() = LocalStrangeSpacing.currentValue

val StyleScope.motion: StxMotion
    get() = LocalStrangeMotion.currentValue
