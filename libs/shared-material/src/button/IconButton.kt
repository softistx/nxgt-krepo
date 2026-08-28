package com.strange.material.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.strange.material.icon.Icon
import com.strange.material.icon.IconSize
import com.strange.material.theme.StrangeTheme
import com.strange.material.theme.ToneColors
import androidx.compose.material3.IconButton as MaterialIconButton

/**
 * An icon on its own, as a button.
 *
 * Material 3 ships one composable per variant — `FilledIconButton`, `FilledTonalIconButton`,
 * `OutlinedIconButton`, `IconButton` — so this maps [ButtonVariant] onto them rather than painting
 * a square itself. That is where the 48 dp touch target and the ripple come from; a hand-built
 * `Box` had to state the target as a magic number and still had no ripple.
 *
 * `description` is not defaulted. An icon button with no label is the one place a missing
 * `contentDescription` makes the control unusable rather than merely undescribed.
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
    val styled = modifier.styleable(styleState, buttonStyle, style)
    val tone = iconButtonTone(color)
    val content = @Composable { Icon(icon = icon, description = description, size = size) }

    when (variant) {
        ButtonVariant.Filled -> {
            FilledIconButton(
                onClick = onClick,
                modifier = styled,
                enabled = enabled,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = tone.main,
                        contentColor = tone.onMain,
                    ),
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Tonal -> {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = styled,
                enabled = enabled,
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = tone.container,
                        contentColor = tone.onContainer,
                    ),
                interactionSource = interactionSource,
                content = content,
            )
        }

        ButtonVariant.Outlined -> {
            OutlinedIconButton(
                onClick = onClick,
                modifier = styled,
                enabled = enabled,
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = tone.main),
                interactionSource = interactionSource,
                content = content,
            )
        }

        // Material 3 has no borderless-underlined icon button, and an underline under an icon
        // would mean nothing anyway: Link falls back to the plain one.
        ButtonVariant.Ghost, ButtonVariant.Link -> {
            MaterialIconButton(
                onClick = onClick,
                modifier = styled,
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(contentColor = tone.main),
                interactionSource = interactionSource,
                content = content,
            )
        }
    }
}

/**
 * Neutral means "quiet" on an icon button rather than "inverted": a filled neutral icon button
 * drawn with `onSurface` on `surface`, which is what the text button scale wants, reads as a black
 * square here.
 */
@Composable
@ReadOnlyComposable
private fun iconButtonTone(color: ButtonColor): ToneColors {
    val scheme = StrangeTheme.colors.scheme
    return when (color) {
        ButtonColor.Neutral -> {
            ToneColors(
                main = scheme.onSurfaceVariant,
                onMain = scheme.onSurface,
                container = scheme.surfaceContainerHigh,
                onContainer = scheme.onSurfaceVariant,
            )
        }

        else -> {
            buttonTone(color)
        }
    }
}
