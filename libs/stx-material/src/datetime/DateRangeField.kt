package com.strange.material.datetime

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
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
 * A start–end date in one field. Material 3's `DateRangePicker` in a dialog.
 */
@Composable
fun DateRangeField(
    start: LocalDate?,
    end: LocalDate?,
    onValueChange: (LocalDate?, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Dates",
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = start?.toEpochMillis(),
            initialSelectedEndDateMillis = end?.toEpochMillis(),
        )
    val shown =
        when {
            start != null && end != null -> "${start.format()} → ${end.format()}"
            start != null -> start.format()
            else -> ""
        }
    TextField(
        value = shown,
        onValueChange = {},
        modifier = modifier,
        label = label,
        readOnly = true,
        enabled = enabled,
        trailing = {
            IconButton(
                icon = StrangeIcons.Calendar,
                description = "Pick dates",
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
                        onValueChange(
                            state.selectedStartDateMillis?.toLocalDate(),
                            state.selectedEndDateMillis?.toLocalDate(),
                        )
                        open = false
                    },
                )
            },
            dismissButton = {
                Button(text = "Cancel", onClick = { open = false }, variant = ButtonVariant.Ghost)
            },
        ) {
            DateRangePicker(state = state)
        }
    }
}
