package com.softistx.material.components.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.IconButton
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.LocalStxStrings
import com.softistx.material.foundation.theme.StxTheme

/**
 * A message box with send, and optional attach. Material 3 has no composer.
 *
 * Return still inserts a newline — that is what [TextareaField] is for. Send is the button, so a
 * reader on a desktop keyboard is not fighting the field for Enter. [onAttach] missing hides
 * attach; [sendEnabled] defaults to "there is something to send". [style] dresses the message
 * field; the two buttons keep the button family's.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = LocalStxStrings.current.writeMessage,
    enabled: Boolean = true,
    sendEnabled: Boolean = value.isNotBlank(),
    onAttach: (() -> Unit)? = null,
    style: Style = Style,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
    ) {
        if (onAttach != null) {
            IconButton(
                icon = StxIcons.Attach,
                description = LocalStxStrings.current.attachFile,
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
            style = style,
        )
        IconButton(
            icon = StxIcons.Send,
            description = LocalStxStrings.current.send,
            onClick = onSend,
            enabled = enabled && sendEnabled,
        )
    }
}
