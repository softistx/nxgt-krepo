package com.strange.material.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strange.material.icon.Icon
import com.strange.material.motion.Transitions
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

/**
 * A button that drops its label when the space it is given gets tight, and grows it back when the
 * space returns — the same button on a desktop toolbar and in a phone's app bar.
 *
 * The caller passes no breakpoint and reads no window size: [collapseBelow] is measured against the
 * width this button is actually offered, so it folds inside a narrow pane on a wide screen too. The
 * label is never simply dropped — it becomes the icon's `contentDescription`, so the collapsed
 * button still announces itself.
 */
@Composable
fun ResponsiveButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    color: ButtonColor = ButtonColor.Primary,
    enabled: Boolean = true,
    collapseBelow: Dp = 360.dp,
    style: Style = Style,
) {
    BoxWithConstraints(modifier = modifier) {
        val expanded = maxWidth >= collapseBelow
        val enter = Transitions.widen
        val exit = Transitions.narrow
        ButtonSurface(
            onClick = onClick,
            variant = variant,
            color = color,
            enabled = enabled,
            style = style,
        ) {
            Icon(icon = icon, description = if (expanded) null else text)
            AnimatedVisibility(visible = expanded, enter = enter, exit = exit) {
                Typography(text = text, variant = TypographyVariant.LabelLarge, maxLines = 1)
            }
        }
    }
}
