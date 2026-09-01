package com.softistx.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.softistx.material.text.Emphasis
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme

/**
 * A control's label, with the two things a label usually has to say beside it.
 *
 * **Optional is marked, not required.** A form where most fields are required and three carry an
 * asterisk reads as a form where three fields matter; marking the rare case is both quieter and
 * more informative. Set [required] when the exception runs the other way — one required field among
 * many optional ones — and the asterisk comes back.
 *
 * The marker is hidden from the screen reader: "Email star" is not a label, and the control itself
 * carries its required state in its own semantics.
 */
@Composable
fun ExtendedLabel(
    text: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    optional: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Typography(text = text, variant = TypographyVariant.LabelMedium)
        if (required) {
            Typography(
                text = "*",
                variant = TypographyVariant.LabelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        if (optional) {
            Typography(
                text = "Optional",
                variant = TypographyVariant.Caption,
                emphasis = Emphasis.Subtle,
            )
        }
        trailing?.invoke()
    }
}
