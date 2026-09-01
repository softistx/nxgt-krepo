package com.softistx.material.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import androidx.compose.material3.Button as MaterialButton

/**
 * A button. `Button("Save changes", onClick = ::save)` is the whole common case.
 *
 * It is Material 3's `Button` with this library's vocabulary in front of it — five variants and
 * seven colours instead of five separate composables and a `ButtonColors` to hand-build. M3 keeps
 * the ripple, the 48 dp touch target, the semantics and the disabled treatment; [buttonStyle] adds
 * the press scale M3 has no parameter for.
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
        Typography(
            text = text,
            // A link reads as text in a sentence, so it keeps the underline M3's Button has no
            // parameter for. Everything else is a label.
            variant =
                if (variant == ButtonVariant.Link) {
                    TypographyVariant.Link
                } else {
                    TypographyVariant.LabelLarge
                },
        )
    }
}

/** The same button with a content slot, for a label that is more than a string. */
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
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    MaterialButton(
        onClick = onClick,
        modifier = modifier.styleable(styleState, buttonStyle, style),
        enabled = enabled,
        shape = CircleShape,
        colors = buttonColors(variant, color),
        elevation = null,
        border = buttonBorder(variant, color, enabled),
        contentPadding = contentPadding(variant),
        interactionSource = interactionSource,
        content = content,
    )
}

/** A link is text in a sentence, so it keeps no padding of its own; the rest use M3's. */
private fun contentPadding(variant: ButtonVariant): PaddingValues =
    if (variant == ButtonVariant.Link) {
        PaddingValues(0.dp)
    } else {
        ButtonDefaults.ContentPadding
    }
