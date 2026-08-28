package com.strange.material.demo.knobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme

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
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StrangeTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.lg),
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
}
