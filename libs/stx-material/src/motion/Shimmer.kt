package com.softistx.material.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.softistx.material.theme.StrangeTheme

/**
 * The travelling highlight that says "this is loading", as one modifier.
 *
 * A placeholder that merely sits there in grey reads as a broken layout; the same shape with a
 * slow sweep across it reads as work in progress. Callers get it by applying the modifier — there
 * is no animation to wire up, which is the whole point.
 *
 * When [StrangeTheme.motion] has motion disabled the sweep stops and the surface stays flat, so a
 * screenshot test is stable without the caller doing anything.
 */
fun Modifier.shimmer(): Modifier =
    composed {
        val motion = StrangeTheme.motion
        val base = MaterialTheme.colorScheme.surfaceContainerHighest
        val highlight = MaterialTheme.colorScheme.surfaceContainerLow

        if (!motion.enabled) {
            return@composed background(base)
        }

        val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    // A sweep is a loop rather than a state change, so it names its own
                    // cadence; MotionScheme has no spec for something that never settles.
                    animation = tween(durationMillis = SWEEP_MILLIS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerProgress",
        )
        background(shimmerBrush(base, highlight, progress))
    }

/**
 * A three-stop gradient slid across the surface by [progress].
 *
 * The sweep is wider than the shape and starts off its leading edge, so the highlight enters and
 * leaves rather than materialising in the middle.
 */
private fun shimmerBrush(
    base: Color,
    highlight: Color,
    progress: Float,
): Brush {
    val travel = 2000f
    val start = progress * travel - travel / 2
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + travel / 2, 0f),
    )
}

/** One pass of the highlight across the placeholder. */
private const val SWEEP_MILLIS = 1200
