package com.softistx.material.components.surface

import androidx.compose.foundation.style.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonColor
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * A two-action confirmation. Material 3's `AlertDialog`.
 *
 * ```kotlin
 * ConfirmDialog(visible, "Delete order?", "This cannot be undone.", destructive = true, …)
 * ```
 *
 * [destructive] paints the confirm action as danger. The dialog is not composed when hidden, so a
 * screen can keep the call at the bottom of its tree without paying for a window. [style] has no
 * default look: M3 paints the dialog's container over a background.
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
    style: Style = Style,
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
        modifier = modifier.styledBy(style),
        dismissButton = {
            Button(text = dismiss, onClick = onDismiss, variant = ButtonVariant.Ghost)
        },
        title = { Typography(text = title, variant = TypographyVariant.TitleLarge) },
        text = { Typography(text = text) },
    )
}
