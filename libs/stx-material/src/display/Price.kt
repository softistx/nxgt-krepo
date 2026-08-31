package com.strange.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import com.strange.material.text.Emphasis
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.Tone

/**
 * An amount, already formatted, with an optional compare-at and period.
 *
 * The library does not know a currency — [amount] is `"€12"` or `"$9.99"` because only the host
 * knows the locale. [compareAt] is struck through. [period] is `"/mo"`, not a second number.
 * A [Stat] is a dashboard figure; this is a price.
 */
@Composable
fun Price(
    amount: String,
    modifier: Modifier = Modifier,
    compareAt: String? = null,
    period: String? = null,
    tone: Tone? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        if (compareAt != null) {
            Typography(
                text = compareAt,
                variant = TypographyVariant.TitleMedium,
                emphasis = Emphasis.Medium,
                decoration = TextDecoration.LineThrough,
            )
        }
        Typography(
            text = amount,
            variant = TypographyVariant.Metric,
            color = tone?.let { StrangeTheme.colors.tone(it).main } ?: Color.Unspecified,
        )
        if (period != null) {
            Typography(text = period, variant = TypographyVariant.LabelLarge, emphasis = Emphasis.Medium)
        }
    }
}
