package com.softistx.material.components.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.IconButton
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import com.softistx.material.foundation.theme.StxTheme

/** Steps [value] by [delta] and keeps it inside [range]. */
fun stepQuantity(
    value: Int,
    delta: Int,
    range: IntRange,
): Int = (value + delta).coerceIn(range)

/**
 * A whole number chosen with plus and minus. Material 3 has no stepper.
 *
 * Each end is an [IconButton], so the 48 dp target is M3's. The value sits between them as
 * [TypographyVariant.Metric]. [range] disables the button that would leave it. [style] dresses the
 * row with no default of its own: the two buttons keep the button family's.
 */
@Composable
fun QuantityField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..999,
    label: String? = null,
    helper: String? = null,
    enabled: Boolean = true,
    style: Style = Style,
) {
    FieldScaffold(modifier = modifier, label = label, helper = helper) {
        Row(
            modifier = Modifier.styledBy(style, enabled = enabled),
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                icon = StxIcons.Minus,
                description = "Decrease",
                onClick = { onValueChange(stepQuantity(value, -1, range)) },
                enabled = enabled && value > range.first,
            )
            Typography(text = value.toString(), variant = TypographyVariant.Metric)
            IconButton(
                icon = StxIcons.Add,
                description = "Increase",
                onClick = { onValueChange(stepQuantity(value, 1, range)) },
                enabled = enabled && value < range.last,
            )
        }
    }
}
