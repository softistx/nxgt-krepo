package com.strange.material.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Immutable

/** Material 3's own expressive curves, resolved once so two default themes compare equal. */
private val ExpressiveScheme: MotionScheme = MotionScheme.expressive()

/**
 * How fast, along whichever of the two axes was asked for.
 *
 * There is no `instant`: a change that should not be seen is not an animation, and
 * `StrangeMotion(enabled = false)` is how a caller turns the clock off.
 */
enum class MotionSpeed { Fast, Default, Slow }

/**
 * Every duration and curve in this library, taken from Material 3's [MotionScheme] rather than
 * invented beside it.
 *
 * M3 splits motion along an axis worth keeping: **spatial** for anything that moves or resizes —
 * springy, allowed to overshoot — and **effects** for colour and alpha, where an overshoot would
 * mean a colour that was never in the palette. A fade and a slide in the same transition are not
 * the same curve, and this is what says so.
 *
 * The scheme is *held*, not read from the composition, because a `Style` block is not a composable
 * scope: `pressed { animate(motion.spatial(Fast)) { … } }` runs at apply time. [StrangeTheme]
 * installs the same scheme it hands `MaterialExpressiveTheme`, so a plain M3 component and one of
 * ours animate identically.
 *
 * **`enabled = false` collapses every spec to [snap], leaving the composables untouched** — a
 * reduced-motion preference, or a screenshot test, is one flag at the theme rather than a branch
 * in each component.
 */
@Immutable
data class StrangeMotion(
    val scheme: MotionScheme = ExpressiveScheme,
    val enabled: Boolean = true,
) {
    /**
     * Position, size, rotation — anything the eye tracks across the screen.
     *
     * **It is a spring damped below 1, so it goes past its target and comes back.** That is the
     * whole point of it, and it is also a trap: whatever it drives has to tolerate a value briefly
     * outside the range it was sent between. `Modifier.offset` does; `Modifier.padding` throws
     * *Padding must be non-negative*, `Modifier.size` and a `Constraints` throw the same way, and
     * an alpha past 1 is silently clamped. When the sink cannot take it, the value belongs on
     * [effects] — or the two belong on separate animations, which is what `Modifier.animateStagger`
     * and [Transitions] both do.
     */
    fun <T> spatial(speed: MotionSpeed = MotionSpeed.Default): FiniteAnimationSpec<T> =
        if (!enabled) {
            snap()
        } else {
            when (speed) {
                MotionSpeed.Fast -> scheme.fastSpatialSpec()
                MotionSpeed.Default -> scheme.defaultSpatialSpec()
                MotionSpeed.Slow -> scheme.slowSpatialSpec()
            }
        }

    /**
     * Colour, alpha, elevation — anything that must land exactly on its target value.
     *
     * Critically damped, so it never overshoots. This is the axis for anything whose sink rejects
     * or clamps an out-of-range value.
     */
    fun <T> effects(speed: MotionSpeed = MotionSpeed.Default): FiniteAnimationSpec<T> =
        if (!enabled) {
            snap()
        } else {
            when (speed) {
                MotionSpeed.Fast -> scheme.fastEffectsSpec()
                MotionSpeed.Default -> scheme.defaultEffectsSpec()
                MotionSpeed.Slow -> scheme.slowEffectsSpec()
            }
        }
}
