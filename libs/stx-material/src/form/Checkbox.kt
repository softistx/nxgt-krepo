package com.softistx.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.softistx.material.text.Typography
import com.softistx.material.theme.StxTheme
import androidx.compose.material3.Checkbox as MaterialCheckbox

/**
 * A box and the sentence it belongs to.
 *
 * Material 3 ships the box and stops there, so every caller writes the same `Row` — and most write
 * it wrongly, hanging the click on the box alone. Here the whole row is one toggle: the label is
 * part of the target, and the screen reader is handed a single checkbox node with the label as its
 * name rather than an unlabelled box beside some text.
 */
@Composable
fun Checkbox(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs)) {
        Row(
            modifier =
                modifier.toggleable(
                    value = value,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onValueChange,
                ),
            horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialCheckbox(checked = value, onCheckedChange = null, enabled = enabled)
            Typography(text = label)
        }
        if (supportingText != null || helper != null) {
            HelperText(helper = helper, error = supportingText)
        }
    }
}
