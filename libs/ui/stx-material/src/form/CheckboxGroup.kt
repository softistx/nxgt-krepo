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
                Row(
                    modifier =
                        Modifier.toggleable(
                            value = ticked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { on ->
                                onValueChange(if (on) value + option else value - option)
                            },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialCheckbox(checked = ticked, onCheckedChange = null, enabled = enabled)
                    Typography(text = optionLabel(option))
                }
            }
        }
    }
}
