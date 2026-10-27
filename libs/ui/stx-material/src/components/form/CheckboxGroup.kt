package com.softistx.material.components.form

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.StxTheme

/**
 * Any number of several, held as the set of what is ticked.
 *
 * A set rather than a list of booleans parallel to the options: the value is then the answer
 * ("Email and SMS") instead of the layout ("true, false, true"), and reordering the options cannot
 * silently change what was chosen.
 */
@Composable
fun <T> CheckboxGroup(
    value: Set<T>,
    onValueChange: (Set<T>) -> Unit,
    options: List<T>,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    helper: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    style: Style = Style,
    optionLabel: (T) -> String = { it.toString() },
) {
    FieldScaffold(
        modifier = modifier,
        label = label,
        required = required,
        helper = helper,
        error = supportingText,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs)) {
            options.forEach { option ->
                val ticked = option in value
                OptionRow(
                    ticked = ticked,
                    onToggle = { on -> onValueChange(if (on) value + option else value - option) },
                    label = optionLabel(option),
                    enabled = enabled,
                    style = style,
                )
            }
        }
    }
}

/** One option of a [CheckboxGroup]: its own press state, so a press scales this row alone. */
@Composable
private fun OptionRow(
    ticked: Boolean,
    onToggle: (Boolean) -> Unit,
    label: String,
    enabled: Boolean,
    style: Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    Row(
        modifier =
            Modifier
                .styleable(styleState, toggleStyle, style)
                .toggleable(
                    value = ticked,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    role = Role.Checkbox,
                    onValueChange = onToggle,
                ),
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialCheckbox(checked = ticked, onCheckedChange = null, enabled = enabled)
        Typography(text = label)
    }
}
