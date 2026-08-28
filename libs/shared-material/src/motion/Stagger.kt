package com.strange.material.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strange.material.theme.StrangeTheme
import kotlinx.coroutines.delay

/**
 * A list that arrives in sequence rather than all at once, from the item's index alone.
 *
 * The delay is capped: past a handful of items the eye reads the effect as latency rather than as
 * choreography, so item 20 does not wait two seconds. Beyond [maxIndex] every item shares the last
 * delay, which keeps a long list feeling instant while a short one still cascades.
 */
fun Modifier.animateStagger(
    index: Int,
    stepMillis: Int = 40,
    maxIndex: Int = 8,
    rise: Dp = 12.dp,
): Modifier =
    composed {
        val motion = StrangeTheme.motion
        var shown by remember(index) { mutableStateOf(!motion.enabled) }

        if (motion.enabled) {
            LaunchedEffect(index) {
                delay((index.coerceAtMost(maxIndex) * stepMillis).toLong())
                shown = true
            }
        }

        val progress by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = motion.standardSpec(),
            label = "stagger",
        )
        alpha(progress).graphicsLayer { translationY = (1f - progress) * rise.toPx() }
    }
