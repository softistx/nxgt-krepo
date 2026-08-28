package com.strange.material.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material 3's own ladder, and the rung [StrangeRadii.base] is measured against. */
private val M3ExtraSmall = 4.dp
private val M3Small = 8.dp
private val M3Medium = 12.dp
private val M3Large = 16.dp
private val M3LargeIncreased = 20.dp
private val M3ExtraLarge = 28.dp
private val M3ExtraLargeIncreased = 32.dp
private val M3ExtraExtraLarge = 48.dp

/**
 * How round this product is, as one number.
 *
 * The eight rungs are **Material 3's own**, shifted so that `medium` — M3's middle slot, and the
 * one most components reach for — lands on [base]. At the default the shapes are exactly M3's; a
 * caller that wants a sharper or softer product moves one `Dp` and the whole ladder follows,
 * including the three slots added in 1.11 that a hand-written `Shapes` silently leaves behind.
 *
 * The names are M3's too. A component asks `MaterialTheme.shapes.medium` — or `shapes.medium`
 * inside a `Style` — rather than reaching in here; this type is the *input* to the theme, not a
 * second vocabulary to read from.
 */
@Immutable
data class StrangeRadii(
    val base: Dp = M3Medium,
) {
    private val shift: Dp get() = base - M3Medium

    val extraSmall: Dp get() = rung(M3ExtraSmall)
    val small: Dp get() = rung(M3Small)
    val medium: Dp get() = rung(M3Medium)
    val large: Dp get() = rung(M3Large)
    val largeIncreased: Dp get() = rung(M3LargeIncreased)
    val extraLarge: Dp get() = rung(M3ExtraLarge)
    val extraLargeIncreased: Dp get() = rung(M3ExtraLargeIncreased)
    val extraExtraLarge: Dp get() = rung(M3ExtraExtraLarge)

    /**
     * All eight slots, not the five a `Shapes(…)` call fills by default.
     *
     * `largeIncreased`, `extraLargeIncreased` and `extraExtraLarge` arrived with Material 3
     * expressive. Leaving them out does not fail — it leaves three slots on M3's defaults while
     * the other five follow [base], which is a rounding that looks almost right.
     */
    fun toShapes(): Shapes =
        Shapes(
            extraSmall = RoundedCornerShape(extraSmall),
            small = RoundedCornerShape(small),
            medium = RoundedCornerShape(medium),
            large = RoundedCornerShape(large),
            largeIncreased = RoundedCornerShape(largeIncreased),
            extraLarge = RoundedCornerShape(extraLarge),
            extraLargeIncreased = RoundedCornerShape(extraLargeIncreased),
            extraExtraLarge = RoundedCornerShape(extraExtraLarge),
        )

    /** Shifted, then floored: a small [base] flattens the ladder rather than inverting it. */
    private fun rung(m3: Dp): Dp = (m3 + shift).coerceAtLeast(0.dp)
}
