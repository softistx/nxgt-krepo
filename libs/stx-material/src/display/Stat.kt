package com.softistx.material.display

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.Tone

/**
 * A figure meant to be read at a glance: the number, what it is, and optionally how it moved.
 *
 * Built on [Card] and [TypographyVariant.Metric]. [delta] takes a [Tone] so a rise can be success
 * and a fall can be danger without the caller picking colours.
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
            StatusBadge(text = delta, tone = tone)
        }
    }
}
