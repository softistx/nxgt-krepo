package com.softistx.material.components.button

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * The one action that floats over the content. Material 3's FAB.
 *
 * Pass [text] and it becomes the extended FAB — icon plus label — collapsing to the icon when
 * [expanded] is false. Without [text] it is the round button, which is the common case.
 *
 * It morphs: M3's FAB has no `shapes =` overload, so the corners shrink to the theme's `small` slot
 * under the finger and spring back, at the scheme's fast spatial speed. No press scale.
 */
@Composable
fun Fab(
    icon: ImageVector,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    expanded: Boolean = true,
    color: ButtonColor = ButtonColor.Primary,
    style: Style = Style,
) {
    val tone = buttonTone(color)
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource)
    val styled = modifier.styleable(styleState, buttonStyle, style)
    val leading = @Composable { Icon(icon = icon, description = description) }
    if (text == null) {
        FloatingActionButton(
            onClick = onClick,
            modifier = styled,
            shape = morphingShape(FloatingActionButtonDefaults.shape, interactionSource),
            containerColor = tone.container,
            contentColor = tone.onContainer,
            interactionSource = interactionSource,
            content = leading,
        )
    } else {
        ExtendedFloatingActionButton(
            text = { Typography(text = text, variant = TypographyVariant.LabelLarge) },
            icon = leading,
            onClick = onClick,
            modifier = styled,
            expanded = expanded,
            shape = morphingShape(FloatingActionButtonDefaults.extendedFabShape, interactionSource),
            containerColor = tone.container,
            contentColor = tone.onContainer,
            interactionSource = interactionSource,
        )
    }
}

/**
 * [rest] while the pointer is up, the theme's `small` corners while it is down, and the springs
 * between. A shape is a parameter M3 reads, not a state a `Style` could animate, which is why
 * this one animates a float itself. The shape is stable per ([rest], `small`) pair and reads the
 * fraction while it draws, so a frame of the spring invalidates drawing, not composition.
 */
@Composable
private fun morphingShape(
    rest: Shape,
    interactionSource: MutableInteractionSource,
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val fraction = animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
    val squarer = MaterialTheme.shapes.small
    if (rest !is CornerBasedShape) return rest
    return remember(rest, squarer) { MorphedCorners(rest, squarer, fraction) }
}

/** [from] corners at 0 and [to] corners at 1, each resolved against the size it is drawn at. */
private class MorphedCorners(
    private val from: CornerBasedShape,
    private val to: CornerBasedShape,
    private val fraction: State<Float>,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val at = fraction.value
        fun corner(
            a: CornerSize,
            b: CornerSize,
        ) = CornerSize(lerp(a.toPx(size, density), b.toPx(size, density), at).coerceAtLeast(0f))
        return RoundedCornerShape(
            topStart = corner(from.topStart, to.topStart),
            topEnd = corner(from.topEnd, to.topEnd),
            bottomEnd = corner(from.bottomEnd, to.bottomEnd),
            bottomStart = corner(from.bottomStart, to.bottomStart),
        ).createOutline(size, layoutDirection, density)
    }
}
