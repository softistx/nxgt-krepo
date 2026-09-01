package com.softistx.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.button.IconButton
import com.softistx.material.icon.Icon
import com.softistx.material.icon.StrangeIcons
import com.softistx.material.text.Typography
import com.softistx.material.theme.StrangeTheme

/**
 * The pinned message at the top of a thread. Material 3's `Surface`.
 *
 * An [AnnouncementBar] is an incident; this is a message the room chose to keep. Missing
 * [onDismiss] means it cannot be unpinned from here — pinning is a permission.
 */
@Composable
fun PinBar(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = StrangeTheme.spacing.sm,
                        vertical = StrangeTheme.spacing.xs,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
        ) {
            Icon(icon = StrangeIcons.Pin, description = null)
            Typography(text = text, modifier = Modifier.weight(1f), maxLines = 1)
            if (onDismiss != null) {
                IconButton(
                    icon = StrangeIcons.Close,
                    description = "Unpin",
                    onClick = onDismiss,
                )
            }
        }
    }
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface
    val shape = MaterialTheme.shapes.medium
    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = color,
            contentColor = contentColor,
            shape = shape,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            color = color,
            contentColor = contentColor,
            shape = shape,
            content = content,
        )
    }
}
