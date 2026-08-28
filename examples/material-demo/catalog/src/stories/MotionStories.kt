package com.strange.material.demo.stories

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.strange.material.demo.knobs.enumChoice
import com.strange.material.demo.storyGroup
import com.strange.material.motion.MotionSpeed
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

val MotionStories =
    storyGroup("Motion") {
        story("Spatial and effects") { knobs ->
            val away = knobs.flag("Move", false)
            val speed = knobs.enumChoice("Speed", MotionSpeed.Default)
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.lg)) {
                Typography(
                    text =
                        "Material 3 splits motion in two. Spatial curves are springs and may " +
                            "overshoot; effects curves must land exactly on their target, because " +
                            "an overshooting colour was never in the palette. Flip Move and watch " +
                            "the square pass its mark while the swatch does not.",
                    variant = TypographyVariant.BodyMedium,
                    emphasis = Emphasis.Medium,
                )
                MotionRow(label = "spatial — position", away = away, speed = speed)
                EffectsRow(label = "effects — colour", away = away, speed = speed)
            }
        }

        story("Every speed at once") { knobs ->
            val away = knobs.flag("Move", false)
            Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md)) {
                MotionSpeed.entries.forEach { speed ->
                    MotionRow(label = "spatial — ${speed.name}", away = away, speed = speed)
                }
            }
        }
    }

@Composable
private fun MotionRow(
    label: String,
    away: Boolean,
    speed: MotionSpeed,
) {
    val offset by animateDpAsState(
        targetValue = if (away) 220.dp else 0.dp,
        animationSpec = StrangeTheme.motion.spatial(speed),
        label = label,
    )
    Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs)) {
        Typography(text = label, variant = TypographyVariant.Code, emphasis = Emphasis.Subtle)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                // `offset`, not `padding`: a spatial spec is a spring and overshoots, so the value
                // it drives goes briefly negative. `padding` throws on that; an offset does not.
                modifier =
                    Modifier
                        .offset(x = offset)
                        .size(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(StrangeTheme.colors.scheme.primary),
            )
        }
    }
}

@Composable
private fun EffectsRow(
    label: String,
    away: Boolean,
    speed: MotionSpeed,
) {
    val target =
        if (away) StrangeTheme.colors.scheme.tertiary else StrangeTheme.colors.scheme.primary
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = StrangeTheme.motion.effects(speed),
        label = label,
    )
    Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs)) {
        Typography(text = label, variant = TypographyVariant.Code, emphasis = Emphasis.Subtle)
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(color),
        )
    }
}
