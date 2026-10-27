package com.softistx.material.foundation.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * What the operating system says about motion and contrast, read when the theme enters composition.
 *
 * It is a snapshot, not a subscription: a user who flips the setting while the app is open sees it
 * on the next launch, or the next time the theme is composed afresh. Every platform answers
 * `false` for a setting it cannot read, never an error — an unreadable preference is "no
 * preference", which is also what the user who never touched it has.
 */
@Immutable
data class SystemAccessibility(
    val reduceMotion: Boolean = false,
    val increaseContrast: Boolean = false,
)

/**
 * The OS accessibility settings, from the platform's own source: the animator scale and the
 * contrast level on Android, `UIAccessibility` on iOS, and on desktop the system command each OS
 * offers (see `docs/components.md`), or the `stx.material.reduceMotion` /
 * `stx.material.increaseContrast` system properties, which win over the OS.
 */
@Composable
expect fun systemAccessibility(): SystemAccessibility

/** M3's [MotionScheme] with every spec a [snap]: what a reduced-motion preference installs under the theme. */
internal object SnapMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
