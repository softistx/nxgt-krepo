package com.softistx.material.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * Progress with the amount written next to it.
 *
 * Material 3's indicators do not print a percentage. A determinate wait that hides "45 %" until
 * the tooltip is the wait nobody can read. Leave [progress] null and the label stays the [caption]
 * (or nothing), because an unspecified wait has no number to invent.
 */
@Composable
fun LabeledProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    caption: String? = null,
    kind: ProgressKind = ProgressKind.Linear,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs)) {
        if (caption != null || progress != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (caption != null) {
                    Typography(text = caption, emphasis = Emphasis.Medium)
                }
                if (progress != null) {
                    Typography(
                        text = "${(progress * 100).toInt()}%",
                        variant = TypographyVariant.LabelSmall,
                    )
                }
            }
        }
        Progress(progress = progress, kind = kind)
    }
}
