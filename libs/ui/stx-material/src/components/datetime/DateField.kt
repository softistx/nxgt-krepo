package com.softistx.material.components.datetime

import androidx.compose.foundation.style.Style
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.form.TextField
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.LocalStxStrings
import kotlinx.datetime.LocalDate

/**
 * A date, typed as a field that opens a picker.
 *
 * Material 3's `DatePicker` in a `DatePickerDialog`. The field shows the ISO date; the picker is
 * how it changes. A naked `DatePicker` on a form is what this is for not doing.
 */
@Composable
fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = LocalStxStrings.current.date,
    enabled: Boolean = true,
    style: Style = Style,
) {
    var open by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = value?.toEpochMillis())
    TextField(
        value = value?.format().orEmpty(),
        onValueChange = {},
        modifier = modifier,
        label = label,
        readOnly = true,
        enabled = enabled,
        style = style,
        trailing = {
            IconButton(
                icon = StxIcons.Calendar,
                description = LocalStxStrings.current.pickDate,
                onClick = { if (enabled) open = true },
            )
        },
    )
    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                Button(
                    text = LocalStxStrings.current.ok,
                    onClick = {
                        onValueChange(state.selectedDateMillis?.toLocalDate())
                        open = false
                    },
                )
            },
            dismissButton = {
                Button(text = LocalStxStrings.current.cancel, onClick = { open = false }, variant = ButtonVariant.Ghost)
            },
        ) {
            DatePicker(state = state)
        }
    }
}
