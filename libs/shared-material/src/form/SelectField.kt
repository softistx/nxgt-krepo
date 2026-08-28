package com.strange.material.form

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.strange.material.text.Typography

/**
 * One of many, chosen from a list that is only open while it is being used.
 *
 * Material 3's `ExposedDropdownMenuBox` does the hard parts — the anchor, the popup's width and
 * placement, dismissal, the rotating chevron — so this is the binding plus the read-only text field
 * that is its anchor. The field holds the *value*; [optionLabel] turns it into words, so a menu
 * whose wording changes does not invalidate what was chosen.
 */
@Composable
fun <T> SelectField(
    field: FieldState<T?>,
    options: List<T>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Choose one",
    helper: String? = null,
    enabled: Boolean = true,
    optionLabel: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open ->
            expanded = open
            // Opening and closing the menu is the whole interaction, so closing it is the moment
            // this field has been visited — there is no focus to leave.
            if (!open) field.touch()
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = field.value?.let(optionLabel).orEmpty(),
            onValueChange = {},
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            label = label?.let { { Typography(text = it) } },
            placeholder = { Typography(text = placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = (field.visibleError ?: helper)?.let { { Typography(text = it) } },
            isError = field.showError,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Typography(text = optionLabel(option)) },
                    onClick = {
                        field.change(option)
                        field.touch()
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
