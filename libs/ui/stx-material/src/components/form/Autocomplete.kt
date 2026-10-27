package com.softistx.material.components.form

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography

/**
 * A field that offers matches as you type. Material 3's `ExposedDropdownMenuBox`, editable.
 *
 * [SelectField] is choose-from-a-closed-list. This is type-and-narrow. Matching is a
 * case-insensitive contains; ranking is the caller's if they outgrow that. [isError] and
 * [supportingText] are the other fields': a complaint under the field, in the error colours.
 */
@Composable
fun <T> Autocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Search",
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    style: Style = Style,
    optionLabel: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val needle = value.trim()
    val shown =
        if (needle.isEmpty()) {
            options
        } else {
            options.filter { optionLabel(it).contains(needle, ignoreCase = true) }
        }
    ExposedDropdownMenuBox(
        expanded = expanded && shown.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth()
                    .styledBy(style, enabled = enabled, base = fieldStyle, interactionSource = interactionSource),
            enabled = enabled,
            label = label?.let { { Typography(text = it) } },
            placeholder = { Typography(text = placeholder) },
            supportingText = supportingText?.let { { Typography(text = it) } },
            isError = isError,
            singleLine = true,
            interactionSource = interactionSource,
        )
        ExposedDropdownMenu(
            expanded = expanded && shown.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            shown.forEach { option ->
                DropdownMenuItem(
                    text = { Typography(text = optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        onValueChange(optionLabel(option))
                        expanded = false
                    },
                )
            }
        }
    }
}
