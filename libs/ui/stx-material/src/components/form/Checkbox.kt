package com.softistx.material.components.form

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme
import androidx.compose.material3.Checkbox as MaterialCheckbox

/**
 * A box and the sentence it belongs to.
 *
 * Material 3 ships the box and stops there, so every caller writes the same `Row` — and most write
 * it wrongly, hanging the click on the box alone. Here the whole row is one toggle: the label is
 * part of the target, and the screen reader is handed a single checkbox node with the label as its
 * name rather than an unlabelled box beside some text. [style] dresses that row.
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
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs)) {
        Row(
            modifier =
                Modifier
                    .styleable(styleState, toggleStyle, style)
                    .toggleable(
                        value = value,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        role = Role.Checkbox,
                        onValueChange = onValueChange,
                    ).minimumInteractiveComponentSize(),
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
