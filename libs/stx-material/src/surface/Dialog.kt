package com.strange.material.surface

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.button.Button
import com.strange.material.button.ButtonColor
import com.strange.material.button.ButtonVariant
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

/**
 * A two-action confirmation. Material 3's `AlertDialog`.
 *
 * ```kotlin
 * ConfirmDialog(visible, "Delete order?", "This cannot be undone.", destructive = true, …)
 * ```
 *
 * [destructive] paints the confirm action as danger. The dialog is not composed when hidden, so a
 * screen can keep the call at the bottom of its tree without paying for a window.
 */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirm: String = "Confirm",
    dismiss: String = "Cancel",
    destructive: Boolean = false,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                text = confirm,
                onClick = onConfirm,
                color = if (destructive) ButtonColor.Danger else ButtonColor.Primary,
            )
        },
        modifier = modifier,
        dismissButton = {
            Button(text = dismiss, onClick = onDismiss, variant = ButtonVariant.Ghost)
        },
        title = { Typography(text = title, variant = TypographyVariant.TitleLarge) },
        text = { Typography(text = text) },
    )
}
