package com.strange.material.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.strange.material.icon.Icon
import com.strange.material.icon.IconSize

/**
 * A button that is only an icon.
 *
 * ```kotlin
 * IconButton(Icons.Default.Delete, "Supprimer", onClick = ::delete, color = ButtonColor.Danger)
 * ```
 *
 * [description] is required, because an icon with no label and no description is invisible to a
 * screen reader and there is no sensible default to fall back on. The touch target is 40dp square
 * regardless of the icon inside it, so a row of these is comfortable whatever sizes they carry.
 */
@Composable
fun IconButton(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Ghost,
    color: ButtonColor = ButtonColor.Neutral,
    size: IconSize = IconSize.Medium,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val base = remember(variant, color) { iconButtonStyle(variant, color) }

    Box(
        modifier =
            modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).styleable(styleState, base, style),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, description = description, size = size)
    }
}
