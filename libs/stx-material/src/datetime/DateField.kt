package com.strange.material.datetime

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.strange.material.button.Button
import com.strange.material.button.ButtonVariant
import com.strange.material.button.IconButton
import com.strange.material.form.TextField
import com.strange.material.icon.StrangeIcons
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
    label: String = "Date",
    enabled: Boolean = true,
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
        trailing = {
            IconButton(
                icon = StrangeIcons.Calendar,
                description = "Pick a date",
                onClick = { if (enabled) open = true },
            )
        },
    )
    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                Button(
                    text = "OK",
                    onClick = {
                        onValueChange(state.selectedDateMillis?.toLocalDate())
                        open = false
                    },
                )
            },
            dismissButton = {
                Button(text = "Cancel", onClick = { open = false }, variant = ButtonVariant.Ghost)
            },
        ) {
            DatePicker(state = state)
        }
    }
}
