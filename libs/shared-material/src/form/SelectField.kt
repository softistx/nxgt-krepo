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
 * placement, dismissal, the rotating chevron — so this is that plus the read-only text field that
 * is its anchor. The control reports the *value*; [optionLabel] turns it into words, so a menu
 * whose wording changes does not invalidate what was chosen.
 */
@Composable
fun <T> SelectField(
    value: T?,
    onValueChange: (T) -> Unit,
    options: List<T>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Choose one",
    helper: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    enabled: Boolean = true,
    optionLabel: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value?.let(optionLabel).orEmpty(),
            onValueChange = {},
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            label = label?.let { { Typography(text = it) } },
            placeholder = { Typography(text = placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = (supportingText ?: helper)?.let { { Typography(text = it) } },
            isError = isError,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Typography(text = optionLabel(option)) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
