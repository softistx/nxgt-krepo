package com.softistx.material.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.softistx.material.icon.Icon
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StrangeTheme
import androidx.compose.material3.ToggleButton as MaterialToggleButton

/**
 * A button that stays pressed. Material 3's `ToggleButton`.
 *
 * Follow, pin, list-or-grid — a choice that is still a button, not a switch. [checked] is the
 * caller's. Unchecked is quiet; checked uses the [color] pair so the on-state is the one that
 * claims a meaning.
 */
@Composable
fun ToggleButton(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Tonal,
    color: ButtonColor = ButtonColor.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    style: Style = Style,
) {
    ToggleButtonSurface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        variant = variant,
        color = color,
        enabled = enabled,
        style = style,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon = icon, description = null)
            }
            Typography(text = text, variant = TypographyVariant.LabelLarge)
        }
    }
}

/** The same toggle with a content slot. */
@Composable
fun ToggleButtonSurface(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Tonal,
    color: ButtonColor = ButtonColor.Primary,
    enabled: Boolean = true,
    style: Style = Style,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState =
        rememberUpdatedStyleState(interactionSource) {
            it.isEnabled = enabled
            it.isSelected = checked
        }
    val styled = modifier.styleable(styleState, buttonStyle, style)
    val tone = buttonTone(color)
    val colors =
        when (variant) {
            ButtonVariant.Filled -> {
                ToggleButtonDefaults.toggleButtonColors(
                    containerColor = tone.container,
                    contentColor = tone.onContainer,
                    checkedContainerColor = tone.main,
                    checkedContentColor = tone.onMain,
                )
            }

            ButtonVariant.Tonal -> {
                ToggleButtonDefaults.tonalToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = tone.main,
                    checkedContainerColor = tone.container,
                    checkedContentColor = tone.onContainer,
                )
            }

            ButtonVariant.Outlined, ButtonVariant.Ghost, ButtonVariant.Link -> {
                ToggleButtonDefaults.outlinedToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = tone.main,
                    checkedContainerColor = tone.container,
                    checkedContentColor = tone.onContainer,
                )
            }
        }

    when (variant) {
        ButtonVariant.Filled -> {
            MaterialToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Tonal -> {
            TonalToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Outlined -> {
            OutlinedToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors = colors,
                border = buttonBorder(ButtonVariant.Outlined, color, enabled),
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Ghost, ButtonVariant.Link -> {
            MaterialToggleButton(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = styled,
                enabled = enabled,
                colors = colors,
                interactionSource = interactionSource,
                content = content,
            )
        }
    }
}
