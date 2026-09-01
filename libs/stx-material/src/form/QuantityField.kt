package com.softistx.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.button.IconButton
import com.softistx.material.icon.StrangeIcons
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

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
 * [TypographyVariant.Metric]. [range] disables the button that would leave it.
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
) {
    FieldScaffold(modifier = modifier, label = label, helper = helper) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                icon = StrangeIcons.Minus,
                description = "Decrease",
                onClick = { onValueChange(stepQuantity(value, -1, range)) },
                enabled = enabled && value > range.first,
            )
            Typography(text = value.toString(), variant = TypographyVariant.Metric)
            IconButton(
                icon = StrangeIcons.Add,
                description = "Increase",
                onClick = { onValueChange(stepQuantity(value, 1, range)) },
                enabled = enabled && value < range.last,
            )
        }
    }
}
