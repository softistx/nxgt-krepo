package com.softistx.material.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.softistx.material.motion.MotionSpeed
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.theme.StrangeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Three dots that beat in turn. Material 3 has no typing mark.
 *
 * The pulse walks the effects axis — alpha must land exactly. [label] is what a screen reader
 * hears; the dots are decoration next to it.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    label: String = "Someone is typing",
) {
    var beat by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(TYPING_BEAT_MS)
            beat = (beat + 1) % 3
        }
    }
    Row(
        modifier = modifier.semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        repeat(3) { index ->
            val alpha by animateFloatAsState(
                targetValue = if (index == beat) 1f else 0.35f,
                animationSpec = StrangeTheme.motion.effects(MotionSpeed.Fast),
                label = "typingDot$index",
            )
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .alpha(alpha)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        Typography(text = label, emphasis = Emphasis.Medium)
    }
}

private const val TYPING_BEAT_MS = 280L
