package com.softistx.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * A quoted passage, with an optional attribution. Material 3's `VerticalDivider` plus the words.
 *
 * The rule is the line, not italics: a quote that only changes typeface disappears in a body of
 * the same size. The divider is M3's; the gap is ours.
 */
@Composable
fun QuoteBlock(
    text: String,
    modifier: Modifier = Modifier,
    attribution: String? = null,
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs)) {
            Typography(text = text, variant = TypographyVariant.BodyLarge)
            if (attribution != null) {
                Typography(
                    text = attribution,
                    variant = TypographyVariant.LabelSmall,
                    emphasis = Emphasis.Medium,
                )
            }
        }
    }
}
