package com.softistx.material.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.softistx.material.theme.StrangeTheme

/**
 * The enter and exit pairs the components are built from.
 *
 * Each half picks its own axis: a fade is an *effects* change and must land on its target alpha,
 * while a slide or a scale is *spatial* and may overshoot. Giving both halves one curve — which is
 * what this file did before it was built on `MotionScheme` — makes a combined transition look
 * subtly wrong, the fade finishing while the slide is still settling.
 *
 * Exits are one speed faster than their entrances throughout: something leaving should get out of
 * the way, not be watched.
 */
object Transitions {
    val fade: EnterTransition
        @Composable @ReadOnlyComposable
        get() = fadeIn(StrangeTheme.motion.effects())

    val fadeAway: ExitTransition
        @Composable @ReadOnlyComposable
        get() = fadeOut(StrangeTheme.motion.effects(MotionSpeed.Fast))

    /** Content arriving from below — a card, an alert, a newly opened story. */
    val riseIn: EnterTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeIn(StrangeTheme.motion.effects()) +
                slideInVertically(StrangeTheme.motion.spatial()) { it / 6 }

    val sinkOut: ExitTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeOut(StrangeTheme.motion.effects(MotionSpeed.Fast)) +
                slideOutVertically(StrangeTheme.motion.spatial(MotionSpeed.Fast)) { it / 6 }

    /** Something that belongs to the point it appeared from — a menu, a popover, a badge. */
    val popIn: EnterTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeIn(StrangeTheme.motion.effects(MotionSpeed.Fast)) +
                scaleIn(StrangeTheme.motion.spatial(MotionSpeed.Fast), initialScale = 0.92f)

    val popOut: ExitTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeOut(StrangeTheme.motion.effects(MotionSpeed.Fast)) +
                scaleOut(StrangeTheme.motion.spatial(MotionSpeed.Fast), targetScale = 0.92f)

    // A label appearing beside something already on screen — a button's text next to its icon.
    val widen: EnterTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeIn(StrangeTheme.motion.effects(MotionSpeed.Fast)) +
                expandHorizontally(StrangeTheme.motion.spatial())

    val narrow: ExitTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeOut(StrangeTheme.motion.effects(MotionSpeed.Fast)) +
                shrinkHorizontally(StrangeTheme.motion.spatial(MotionSpeed.Fast))

    /** Content that takes or gives back vertical room: an accordion, a helper message. */
    val expand: EnterTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeIn(StrangeTheme.motion.effects(MotionSpeed.Fast)) +
                expandVertically(StrangeTheme.motion.spatial())

    val collapse: ExitTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeOut(StrangeTheme.motion.effects(MotionSpeed.Fast)) +
                shrinkVertically(StrangeTheme.motion.spatial(MotionSpeed.Fast))
}
