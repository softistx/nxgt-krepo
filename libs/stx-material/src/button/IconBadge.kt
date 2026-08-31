package com.strange.material.button

import androidx.compose.foundation.style.Style
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.strange.material.display.StatusBadge
import com.strange.material.icon.IconSize
import com.strange.material.theme.Tone

/**
 * An [IconButton] wearing a count. Material 3's `BadgedBox`.
 *
 * Inbox, notifications — the icon is the destination, the badge is how many are waiting. A
 * count of zero shows no badge, so an empty inbox is not a red zero. Above 99 the label is
 * `99+`, which is the size a pill can hold.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0,
    tone: Tone = Tone.Error,
    variant: ButtonVariant = ButtonVariant.Ghost,
    color: ButtonColor = ButtonColor.Neutral,
    size: IconSize = IconSize.Medium,
    enabled: Boolean = true,
    style: Style = Style,
) {
    BadgedBox(
        badge = {
            if (count > 0) {
                StatusBadge(text = if (count > 99) "99+" else count.toString(), tone = tone)
            }
        },
        modifier = modifier,
    ) {
        IconButton(
            icon = icon,
            description = description,
            onClick = onClick,
            variant = variant,
            color = color,
            size = size,
            enabled = enabled,
            style = style,
        )
    }
}
