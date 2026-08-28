package com.strange.material.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.strange.material.theme.StrangeTheme

/**
 * The named entrances and exits, built from the theme's motion tokens.
 *
 * A component asks for a *kind* of appearance rather than assembling one, which is what keeps two
 * surfaces written months apart from arriving differently. Every one of these reads its duration
 * and easing from [StrangeTheme.motion], so turning motion off turns all of them off at once.
 */
object Transitions {
    /** A surface simply appearing in place — a message, a badge, an empty state. */
    val fade: EnterTransition
        @Composable @ReadOnlyComposable
        get() = fadeIn(StrangeTheme.motion.standardSpec())

    val fadeAway: ExitTransition
        @Composable @ReadOnlyComposable
        get() = fadeOut(StrangeTheme.motion.quickSpec())

    /** Something arriving from below: a sheet, a toast, a row entering a list. */
    val riseIn: EnterTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeIn(StrangeTheme.motion.standardSpec()) +
                slideInVertically(StrangeTheme.motion.standardSpec()) { it / 6 }

    val sinkOut: ExitTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeOut(StrangeTheme.motion.quickSpec()) +
                slideOutVertically(StrangeTheme.motion.quickSpec()) { it / 6 }

    /** Something anchored to a trigger: a menu, a popover, a tooltip. */
    val popIn: EnterTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeIn(StrangeTheme.motion.quickSpec()) +
                scaleIn(StrangeTheme.motion.quickSpec(), initialScale = 0.92f)

    val popOut: ExitTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeOut(StrangeTheme.motion.quickSpec()) +
                scaleOut(StrangeTheme.motion.quickSpec(), targetScale = 0.92f)

    /** Content that takes or gives back vertical room: an accordion, a helper message. */
    val expand: EnterTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeIn(StrangeTheme.motion.quickSpec()) +
                expandVertically(StrangeTheme.motion.standardSpec())

    val collapse: ExitTransition
        @Composable @ReadOnlyComposable
        get() =
            fadeOut(StrangeTheme.motion.quickSpec()) +
                shrinkVertically(StrangeTheme.motion.standardSpec())
}
