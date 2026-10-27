package com.softistx.material.components.button

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.feedback.Progress
import com.softistx.material.components.feedback.ProgressKind

/**
 * A button that is in flight. Material 3's `Button`, with the label replaced by a spinner.
 *
 * [busy] disables the click and swaps the text for a circular [Progress], so the reader cannot
 * tap twice while a save is running. `LoadMoreButton` is the same idea for a keyset page; this
 * is the idea for any action.
 */
@Composable
fun BusyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    variant: ButtonVariant = ButtonVariant.Filled,
    color: ButtonColor = ButtonColor.Primary,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Small,
    style: Style = Style,
) {
    ButtonSurface(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        color = color,
        enabled = enabled && !busy,
        style = style,
        size = size,
    ) {
        if (busy) {
            Progress(modifier = Modifier.size(ButtonDefaults.iconSizeFor(size.height)), kind = ProgressKind.Circular)
        } else {
            ButtonLabel(text, variant, size)
        }
    }
}
