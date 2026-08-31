package com.strange.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.button.IconButton
import com.strange.material.icon.StrangeIcons
import com.strange.material.theme.StrangeTheme

/**
 * A message box with send, and optional attach. Material 3 has no composer.
 *
 * Return still inserts a newline — that is what [TextareaField] is for. Send is the button, so a
 * reader on a desktop keyboard is not fighting the field for Enter. [onAttach] missing hides
 * attach; [sendEnabled] defaults to "there is something to send".
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Write a message",
    enabled: Boolean = true,
    sendEnabled: Boolean = value.isNotBlank(),
    onAttach: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        if (onAttach != null) {
            IconButton(
                icon = StrangeIcons.Attach,
                description = "Attach a file",
                onClick = onAttach,
                enabled = enabled,
            )
        }
        TextareaField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = placeholder,
            enabled = enabled,
            minLines = 1,
            maxLines = 4,
        )
        IconButton(
            icon = StrangeIcons.Send,
            description = "Send",
            onClick = onSend,
            enabled = enabled && sendEnabled,
        )
    }
}
