package com.strange.material.demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.motion.Transitions
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

/**
 * The middle pane. The story swaps through the library's own transitions rather than appearing
 * abruptly — the catalogue would be a poor advertisement for a motion layer it did not use.
 */
@Composable
fun StoryStage(
    story: Story,
    knobs: KnobsStore,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(StrangeTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.md),
    ) {
        Typography(text = story.name, variant = TypographyVariant.HeadlineSmall)
        Typography(text = story.id, variant = TypographyVariant.Code, emphasis = Emphasis.Subtle)
        // `transitionSpec` is not a composable scope, so the transitions — which read the motion
        // tokens off the theme — are resolved here and captured.
        val enter = Transitions.riseIn
        val exit = Transitions.fadeAway
        AnimatedContent(
            targetState = story,
            transitionSpec = { enter togetherWith exit },
            label = "story",
        ) { current ->
            current.content(knobs.of(current))
        }
    }
}
