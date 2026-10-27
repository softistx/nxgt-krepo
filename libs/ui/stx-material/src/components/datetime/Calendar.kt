package com.softistx.material.components.datetime

import androidx.compose.foundation.style.Style
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy
import kotlinx.datetime.LocalDate

/**
 * An inline calendar. Material 3's `DatePicker`, not in a dialog.
 *
 * Use [DateField] on a form; use this when the calendar *is* the page. [style] has no default: the
 * picker's container, days and selection are M3's `DatePickerDefaults`, which dims them itself.
 */
@Composable
fun Calendar(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    style: Style = Style,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = value?.toEpochMillis())
    LaunchedEffect(state.selectedDateMillis) {
        val next = state.selectedDateMillis?.toLocalDate()
        if (next != value) onValueChange(next)
    }
    DatePicker(state = state, modifier = modifier.styledBy(style), showModeToggle = false)
}
