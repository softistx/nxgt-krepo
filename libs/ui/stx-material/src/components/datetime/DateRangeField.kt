package com.softistx.material.components.datetime

import androidx.compose.foundation.style.Style
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
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
    style: Style = Style,
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
        style = style,
        trailing = {
            IconButton(
                icon = StxIcons.Calendar,
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
