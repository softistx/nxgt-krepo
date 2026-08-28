package com.strange.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme
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
    field: FieldState<Boolean>,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs)) {
        Row(
            modifier =
                modifier.toggleable(
                    value = field.value,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { next ->
                        field.change(next)
                        field.touch()
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialCheckbox(checked = field.value, onCheckedChange = null, enabled = enabled)
            Typography(text = label)
        }
        if (field.visibleError != null || helper != null) {
            HelperText(helper = helper, error = field.visibleError)
        }
    }
}
