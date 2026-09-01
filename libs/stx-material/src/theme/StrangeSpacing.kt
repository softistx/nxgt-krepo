package com.softistx.material.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale, in one place so a component never writes a bare `12.dp`.
 *
 * The steps are a geometric-ish progression rather than a linear one, because a linear scale gives
 * two adjacent steps that read as the same distance and forces a third. Callers reach for a step
 * by role — [xs] between a label and its field, [md] between cards, [xl] between sections — and a
 * layout stays coherent because everything in it snapped to the same ladder.
 */
@Immutable
data class StrangeSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
) {
    /**
     * The same ladder scaled by [factor], for a denser or roomier surface.
     *
     * Scaling the whole scale keeps the relationships between the steps intact, which is the point
     * of having a scale — a density knob that only shrank the gaps a caller happened to notice
     * would leave the rest of the layout at the old rhythm.
     */
    fun scaledBy(factor: Float): StrangeSpacing =
        StrangeSpacing(
            none = none * factor,
            xxs = xxs * factor,
            xs = xs * factor,
            sm = sm * factor,
            md = md * factor,
            lg = lg * factor,
            xl = xl * factor,
            xxl = xxl * factor,
        )
}
