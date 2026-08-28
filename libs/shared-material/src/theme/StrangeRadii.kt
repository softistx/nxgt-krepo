package com.strange.material.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner-radius scale, derived from a single [base].
 *
 * One number decides how round the whole product looks — the React library spells this `--radius`
 * and derives the rest with `calc()`. Deriving rather than listing is what makes "make everything
 * a bit softer" a one-line change instead of an eight-line one, and it guarantees the steps stay
 * ordered however far the base is pushed.
 *
 * [full] is deliberately not derived: a pill is a pill at any base, and clamping it to a large
 * radius would make tall pills look like rounded rectangles.
 */
@Immutable
data class StrangeRadii(
    val base: Dp = 10.dp,
) {
    val none: Dp get() = 0.dp
    val sm: Dp get() = (base - 6.dp).coerceAtLeast(0.dp)
    val md: Dp get() = (base - 3.dp).coerceAtLeast(0.dp)
    val lg: Dp get() = base
    val xl: Dp get() = base + 6.dp
    val xxl: Dp get() = base + 18.dp
    val full: Dp get() = 1000.dp

    /** The M3 [Shapes] this scale implies, so `MaterialTheme.shapes` agrees with the tokens. */
    fun toShapes(): Shapes =
        Shapes(
            extraSmall = RoundedCornerShape(sm),
            small = RoundedCornerShape(md),
            medium = RoundedCornerShape(lg),
            large = RoundedCornerShape(xl),
            extraLarge = RoundedCornerShape(xxl),
        )
}
