package com.softistx.material.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.StatusDot
import com.softistx.material.foundation.text.Emphasis
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme
import com.softistx.material.foundation.theme.Tone

/**
 * A figure meant to be read at a glance: the number, what it is, and optionally how it moved.
 *
 * Built on [Card] and [TypographyVariant.Metric]. The [delta] is a line of text; its [Tone] adds a
 * coloured dot only when it carries meaning (success, warning, error). [Tone.Info], the default,
 * is a plain figure with no dot, so a row of stats does not compete for the screen's one accent.
 */
@Composable
fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    tone: Tone = Tone.Info,
) {
    Card(modifier = modifier) {
        Typography(text = label, variant = TypographyVariant.LabelLarge, emphasis = Emphasis.Medium)
        Typography(text = value, variant = TypographyVariant.Metric)
        if (delta != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tone != Tone.Info) StatusDot(tone = tone, description = null)
                Typography(text = delta, variant = TypographyVariant.LabelMedium, emphasis = Emphasis.Medium)
            }
        }
    }
}
