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
 * Any number of several, held as the set of what is ticked.
 *
 * A set rather than a list of booleans parallel to the options: the field then holds the answer
 * ("Email and SMS") instead of the layout ("true, false, true"), and reordering the options cannot
 * silently change what was chosen.
 */
@Composable
fun <T> CheckboxGroup(
    field: FieldState<Set<T>>,
    options: List<T>,
    modifier: Modifier = Modifier,
    label: String? = null,
    required: Boolean = false,
    helper: String? = null,
    enabled: Boolean = true,
    optionLabel: (T) -> String = { it.toString() },
) {
    FieldScaffold(
        modifier = modifier,
        label = label,
        required = required,
        helper = helper,
        error = field.visibleError,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs)) {
            options.forEach { option ->
                val ticked = option in field.value
                Row(
                    modifier =
                        Modifier.toggleable(
                            value = ticked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { on ->
                                field.change(if (on) field.value + option else field.value - option)
                                field.touch()
                            },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MaterialCheckbox(checked = ticked, onCheckedChange = null, enabled = enabled)
                    Typography(text = optionLabel(option))
                }
            }
        }
    }
}
