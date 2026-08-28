package com.strange.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.strange.material.text.Typography
import com.strange.material.theme.StrangeTheme

/**
 * One of several, where seeing all of them at once is the point.
 *
 * Material 3 has the button and no group, so the two things that make a group a group are added
 * here: `selectableGroup()`, which is what lets a screen reader say "2 of 4" instead of reading
 * four unrelated buttons, and a row-wide target so the label is clickable.
 *
 * [options] is a list of values and [label] turns one into its wording — the field holds the value,
 * never the string, so a selection survives a change of copy.
 */
@Composable
fun <T> RadioGroup(
    field: FieldState<T?>,
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
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
        ) {
            options.forEach { option ->
                Row(
                    modifier =
                        Modifier.selectable(
                            selected = field.value == option,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                field.change(option)
                                field.touch()
                            },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = field.value == option, onClick = null, enabled = enabled)
                    Typography(text = optionLabel(option))
                }
            }
        }
    }
}
