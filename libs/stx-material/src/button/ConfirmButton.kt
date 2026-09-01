package com.softistx.material.button

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * A button that asks once more. Material 3's `Button`, armed on the first press.
 *
 * The first click changes the label to [confirmText]; the second click fires [onConfirm]. Wait
 * [holdMs] and it goes back to [text], so a missed click is not a delete. For a choice that needs
 * a sentence of warning, [com.softistx.material.surface.ConfirmDialog] is the control.
 */
@Composable
fun ConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm?",
    variant: ButtonVariant = ButtonVariant.Filled,
    color: ButtonColor = ButtonColor.Danger,
    enabled: Boolean = true,
    style: Style = Style,
    holdMs: Long = 3_000L,
) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(holdMs)
            armed = false
        }
    }
    Button(
        text = if (armed) confirmText else text,
        onClick = {
            if (armed) {
                armed = false
                onConfirm()
            } else {
                armed = true
            }
        },
        modifier = modifier,
        variant = variant,
        color = color,
        enabled = enabled,
        style = style,
    )
}
