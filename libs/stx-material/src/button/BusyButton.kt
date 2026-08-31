package com.strange.material.button

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strange.material.feedback.Progress
import com.strange.material.feedback.ProgressKind
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

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
    style: Style = Style,
) {
    ButtonSurface(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        color = color,
        enabled = enabled && !busy,
        style = style,
    ) {
        if (busy) {
            Progress(modifier = Modifier.size(18.dp), kind = ProgressKind.Circular)
        } else {
            Typography(
                text = text,
                variant =
                    if (variant == ButtonVariant.Link) {
                        TypographyVariant.Link
                    } else {
                        TypographyVariant.LabelLarge
                    },
            )
        }
    }
}
