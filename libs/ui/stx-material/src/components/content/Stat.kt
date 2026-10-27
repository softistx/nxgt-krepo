package com.softistx.material.components.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
 *
 * The metric is display-sized and `SemiBold` — M3's emphasised styles are `Medium`, and a figure
 * must not get lighter. Without a [delta], the height of one is still reserved ([reserveDelta]) so
 * stats side by side in a row keep the same height; pass `false` for a stat standing alone. The figure's
 * font scale is capped at 1.3 so a display-sized number still fits half a phone row at large type.
 */
@Composable
fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    tone: Tone = Tone.Info,
    reserveDelta: Boolean = true,
) {
    Card(modifier = modifier) {
        Typography(text = label, variant = TypographyVariant.LabelLarge, emphasis = Emphasis.Medium)
        val density = LocalDensity.current
        // A display-sized figure at 2x font scale is wider than a phone's half row: cap its scale.
        val capped = Density(density.density, density.fontScale.coerceAtMost(METRIC_MAX_FONT_SCALE))
        CompositionLocalProvider(LocalDensity provides capped) {
            Typography(text = value, variant = TypographyVariant.Metric, maxLines = 1)
        }
        if (delta == null && reserveDelta) {
            val line = MaterialTheme.typography.labelMedium.lineHeight
            Spacer(Modifier.height(with(LocalDensity.current) { line.toDp() }))
        }
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

private const val METRIC_MAX_FONT_SCALE = 1.3f
