package com.strange.material.datetime

import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.datetime.LocalDate

/**
 * An inline calendar. Material 3's `DatePicker`, not in a dialog.
 *
 * Use [DateField] on a form; use this when the calendar *is* the page.
 */
@Composable
fun Calendar(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = value?.toEpochMillis())
    LaunchedEffect(state.selectedDateMillis) {
        val next = state.selectedDateMillis?.toLocalDate()
        if (next != value) onValueChange(next)
    }
    DatePicker(state = state, modifier = modifier, showModeToggle = false)
}
