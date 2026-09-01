package com.softistx.material.demo.knobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.demo.PaneScrollbar
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * The right pane. It draws whatever the story asked for, in the order it asked — no story
 * registers its controls anywhere else, so the panel and the preview cannot drift apart.
 */
@Composable
fun KnobsPanel(
    knobs: Knobs,
    modifier: Modifier = Modifier,
) {
    val controls = knobs.controls
    val scrollState = rememberScrollState()
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(StxTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.lg),
        ) {
            Typography(text = "Controls", variant = TypographyVariant.Overline, emphasis = Emphasis.Medium)
            if (controls.isEmpty()) {
                Typography(
                    text = "This story takes no knobs.",
                    variant = TypographyVariant.BodySmall,
                    emphasis = Emphasis.Subtle,
                )
            }
            controls.forEach { KnobControl(knobs = knobs, knob = it) }
        }
        // matchParentSize, not fillMaxHeight on the scrollbar itself: fillMaxHeight participates
        // in measuring this wrap-content Box and stretches it to the pane, which used to leave
        // StoryStage at height 0 — controls visible, preview gone.
        Box(Modifier.matchParentSize()) {
            PaneScrollbar(
                state = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}
