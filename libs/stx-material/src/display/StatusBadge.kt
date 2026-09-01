package com.softistx.material.display

import androidx.compose.material3.Badge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme
import com.softistx.material.theme.Tone

/**
 * A short, inert label carrying a status — "settled", "on hold", "3 failed".
 *
 * Material 3's `Badge` with a [Tone] in front of it, so a caller names the *meaning* and the
 * container and foreground come out of the semantic scale together. `Badge` takes its two colours
 * as parameters and does the rest, including the pill shape and the minimum size that keeps a
 * one-character badge circular.
 *
 * It takes no `Style`: there is nothing here to interact with, and a badge that could be restyled
 * per call site would stop meaning one thing across a screen.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: Tone = Tone.Info,
    modifier: Modifier = Modifier,
) {
    val role = StrangeTheme.colors.tone(tone)
    Badge(
        modifier = modifier,
        containerColor = role.container,
        contentColor = role.onContainer,
    ) {
        Typography(text = text, variant = TypographyVariant.LabelSmall)
    }
}
