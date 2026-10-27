package com.softistx.material.foundation.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Whether the OS asks to reduce motion, read when the theme enters composition.
 *
 * A snapshot, not a subscription: a user who flips the setting while the app is open sees it on
 * the next launch. A platform that cannot read it answers `false`, which is also what the user who
 * never touched the setting has. Android reads the animator scale, iOS `UIAccessibility`; desktop
 * has its own fallback, and `-Dstx.material.reduceMotion=true|false` wins there.
 */
@Composable
expect fun systemReducesMotion(): Boolean

/** M3's [MotionScheme] with every spec a [snap]: what a reduced-motion preference installs under the theme. */
internal object SnapMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
