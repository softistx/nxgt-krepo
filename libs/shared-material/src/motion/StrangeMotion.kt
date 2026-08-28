package com.strange.material.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * Durations and easings as tokens, so no component writes a bare `tween(300)`.
 *
 * Motion is the part of a design system that decays first: one component fades in over 150ms,
 * the next over 220ms, and nobody can say why. Naming the durations by *role* — [quick] for
 * something the finger is already touching, [standard] for a surface appearing, [slow] for a
 * whole screen — means a component picks a name rather than a number, and the product can be
 * retimed in one place.
 *
 * The easings are Material 3's emphasized set. [emphasized] is for something that both enters and
 * leaves, [emphasizedDecelerate] for something arriving and stopping, [emphasizedAccelerate] for
 * something leaving for good.
 */
@Immutable
data class StrangeMotion(
    val instant: Int = 80,
    val quick: Int = 140,
    val standard: Int = 240,
    val slow: Int = 400,
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    /**
     * Whether animations run at all. A caller sets this to false for a screenshot test, a reduced
     * motion preference, or a story that needs to hold still — and every component obeys, because
     * they all read their specs from here rather than building their own.
     */
    val enabled: Boolean = true,
) {
    /** A spec at the named duration, or an instant one when motion is off. */
    fun <T> spec(
        durationMillis: Int,
        easing: Easing = standardEasing,
    ): FiniteAnimationSpec<T> = tween(durationMillis = if (enabled) durationMillis else 0, easing = easing)

    fun <T> quickSpec(): FiniteAnimationSpec<T> = spec(quick, emphasizedDecelerate)

    fun <T> standardSpec(): FiniteAnimationSpec<T> = spec(standard, emphasized)

    fun <T> slowSpec(): FiniteAnimationSpec<T> = spec(slow, emphasized)
}
