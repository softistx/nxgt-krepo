package com.softistx.material.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.icon.Icon
import com.softistx.material.icon.IconSize
import androidx.compose.material3.IconToggleButton as MaterialIconToggleButton

/**
 * An icon that stays pressed. Material 3's `IconToggleButton`.
 *
 * Favourite, pin, bookmark — the same 48 dp target as [IconButton], with a checked colour of its
 * own. [checkedIcon] is the on-state glyph; leave it and the same icon just changes colour.
 */
@Composable
fun IconToggle(
    icon: ImageVector,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedIcon: ImageVector = icon,
    variant: ButtonVariant = ButtonVariant.Ghost,
    color: ButtonColor = ButtonColor.Neutral,
    size: IconSize = IconSize.Medium,
    enabled: Boolean = true,
    style: Style = Style,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState =
        rememberUpdatedStyleState(interactionSource) {
            it.isEnabled = enabled
            it.isSelected = checked
        }
    val styled = modifier.styleable(styleState, buttonStyle, style)
    val tone = iconButtonTone(color)
    val content = @Composable {
        Icon(icon = if (checked) checkedIcon else icon, description = description, size = size)
    }

    when (variant) {
        ButtonVariant.Filled -> {
            FilledIconToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors =
                    IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = tone.container,
                        contentColor = tone.onContainer,
                        checkedContainerColor = tone.main,
                        checkedContentColor = tone.onMain,
                    ),
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Tonal -> {
            FilledTonalIconToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors =
                    IconButtonDefaults.filledTonalIconToggleButtonColors(
                        checkedContainerColor = tone.container,
                        checkedContentColor = tone.onContainer,
                    ),
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Outlined -> {
            OutlinedIconToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors =
                    IconButtonDefaults.outlinedIconToggleButtonColors(
                        contentColor = tone.main,
                        checkedContentColor = tone.main,
                    ),
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Ghost, ButtonVariant.Link -> {
            MaterialIconToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors =
                    IconButtonDefaults.iconToggleButtonColors(
                        contentColor = tone.main,
                        checkedContentColor = tone.main,
                    ),
                interactionSource = interactionSource,
                content = content,
            )
        }
    }
}
