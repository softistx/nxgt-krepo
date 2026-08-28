package com.strange.material.button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

/** The gap between an icon and its label inside a button. Not a theme token: it is the button's
 * own internal rhythm, and a caller changing the spacing scale should not move it. */
private val PartSpacing = 8.dp

/**
 * A button.
 *
 * ```kotlin
 * Button("Enregistrer", onClick = ::save)
 * ```
 *
 * That is the whole of the common case, and it is deliberate: the variant, the colour, the shape,
 * the padding, the minimum target size, the hover response and the press give all have correct
 * defaults. Nothing about a button's *appearance* is a required argument.
 *
 * Everything visual lives in a [Style], so the two axes are named rather than assembled:
 *
 * ```kotlin
 * Button("Supprimer", onClick = ::delete, color = ButtonColor.Danger, variant = ButtonVariant.Outlined)
 * ```
 *
 * and anything the matrix does not cover is a [style] the caller passes, applied last so it wins:
 *
 * ```kotlin
 * Button("Continuer", onClick = ::next, style = Style { minWidth(200.dp) })
 * ```
 *
 * The component holds no presentation state of its own — pressed and hovered come from the
 * [MutableInteractionSource] through `rememberUpdatedStyleState`, and `enabled` is pushed into it
 * so the style's `disabled` block applies. A caller never remembers a boolean for a visual.
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        enabled = enabled,
        style = style,
    ) {
        Typography(text = text, variant = TypographyVariant.LabelLarge)
    }
}

/**
 * The same button with arbitrary content — an icon beside a label, a badge, a spinner.
 *
 * [Button] is this with a [Typography] inside it. Keeping the slot version public is what stops a
 * caller having to copy the file the first time they need an icon.
 */
@Composable
fun ButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    color: ButtonColor = ButtonColor.Primary,
    enabled: Boolean = true,
    style: Style = Style,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState =
        rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val base = remember(variant, color) { buttonStyle(variant, color) }

    Row(
        modifier =
            modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).styleable(styleState, base, style),
        horizontalArrangement =
            Arrangement.spacedBy(PartSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
