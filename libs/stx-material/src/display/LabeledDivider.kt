package com.softistx.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * A label sitting in a hairline. Material 3's divider does not take text.
 *
 * "Or" between two sign-in paths, "Yesterday" in a timeline — the line is what M3 already draws;
 * the word is ours.
 */
@Composable
fun LabeledDivider(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Typography(
            text = label,
            variant = TypographyVariant.LabelSmall,
            emphasis = Emphasis.Subtle,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
