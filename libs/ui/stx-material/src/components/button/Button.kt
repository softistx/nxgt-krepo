package com.softistx.material.components.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * A button. `Button("Save changes", onClick = ::save)` is the whole common case.
 *
 * It is Material 3's `Button` with this library's vocabulary in front of it — five variants and
 * seven colours instead of five separate composables and a `ButtonColors` to hand-build. M3 keeps
 * the ripple, the 48 dp touch target, the semantics and the disabled treatment; [buttonStyle] adds
 * the disabled alpha M3 has no parameter for.
 *
 * It morphs: round at rest, squarer while pressed, through M3's `shapes =` overload, so it takes
 * no press scale. The morph follows `MaterialTheme.motionScheme`. [size] picks one of M3's five
 * heights and brings its padding and label style with it.
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
    size: ButtonSize = ButtonSize.Small,
) {
    ButtonSurface(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        color = color,
        enabled = enabled,
        style = style,
        size = size,
    ) {
        ButtonLabel(text, variant, size)
    }
}

/**
 * The label of a text button: a link keeps the underline M3's Button has no parameter for;
 * everything else is a label at the size's own text style.
 */
@Composable
internal fun ButtonLabel(
    text: String,
    variant: ButtonVariant,
    size: ButtonSize,
) {
    if (variant == ButtonVariant.Link) {
        Typography(text = text, variant = TypographyVariant.Link)
    } else {
        Text(text = text, style = ButtonDefaults.textStyleFor(size.height))
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
    size: ButtonSize = ButtonSize.Small,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    val minHeight = if (variant == ButtonVariant.Link) 0.dp else size.height
    MaterialButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(size.height),
        modifier = modifier.heightIn(min = minHeight).styleable(styleState, morphingButtonStyle, style),
        enabled = enabled,
        colors = buttonColors(variant, color),
        elevation = null,
        border = buttonBorder(variant, color, enabled),
        contentPadding = contentPadding(variant, size),
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * A link is text in a sentence, so it keeps no padding of its own and [size] gives it no height;
 * the rest use M3's padding for the size.
 */
private fun contentPadding(
    variant: ButtonVariant,
    size: ButtonSize,
): PaddingValues =
    if (variant == ButtonVariant.Link) {
        PaddingValues(0.dp)
    } else if (size == ButtonSize.Small) {
        // contentPaddingFor(40.dp) is 16 dp a side; the default button was 24 dp and keeps it.
        ButtonDefaults.ContentPadding
    } else {
        ButtonDefaults.contentPaddingFor(size.height)
    }
