package com.softistx.material.demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.motion.Transitions
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

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
    val scrollState = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(StxTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.md),
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
        PaneScrollbar(
            state = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
