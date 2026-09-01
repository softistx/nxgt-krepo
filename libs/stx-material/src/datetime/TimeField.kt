package com.softistx.material.datetime

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.button.Button
import com.softistx.material.button.ButtonVariant
import com.softistx.material.button.IconButton
import com.softistx.material.form.TextField
import com.softistx.material.icon.StrangeIcons
import kotlinx.datetime.LocalTime

/**
 * A time of day, typed as a field that opens a picker.
 *
 * Material 3's `TimePicker` in an `AlertDialog`. The field shows `HH:mm`; the dial is how it
 * changes. Keyboard-first layouts still get the same field — M3's picker switches layout itself.
 */
@Composable
fun TimeField(
    value: LocalTime?,
    onValueChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Time",
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val state =
        rememberTimePickerState(
            initialHour = value?.hour ?: 0,
            initialMinute = value?.minute ?: 0,
        )
    TextField(
        value = value?.format().orEmpty(),
        onValueChange = {},
        modifier = modifier,
        label = label,
        readOnly = true,
        enabled = enabled,
        trailing = {
            IconButton(
                icon = StrangeIcons.Schedule,
                description = "Pick a time",
                onClick = { if (enabled) open = true },
            )
        },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                Button(
                    text = "OK",
                    onClick = {
                        onValueChange(LocalTime(state.hour, state.minute))
                        open = false
                    },
                )
            },
            dismissButton = {
                Button(text = "Cancel", onClick = { open = false }, variant = ButtonVariant.Ghost)
            },
            text = { TimePicker(state = state) },
        )
    }
}
