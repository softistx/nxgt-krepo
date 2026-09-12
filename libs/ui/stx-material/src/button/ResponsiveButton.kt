package com.softistx.material.button

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.softistx.material.icon.Icon
import com.softistx.material.motion.Transitions
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
import com.softistx.material.theme.StxTheme

/**
 * A button that drops its label when the space it is given gets tight, and grows it back when the
 * space returns — the same button on a desktop toolbar and in a phone's app bar.
 *
 * The caller passes no breakpoint and reads no window size: [collapseBelow] is measured against the
 * width this button is actually offered, so it folds inside a narrow pane on a wide screen too. The
 * label is never simply dropped — it becomes the icon's `contentDescription`, so the collapsed
 * button still announces itself.
 *
 * **Collapsed, it *is* an [IconButton]** — Material 3's own `FilledIconButton` and its siblings,
 * round and square-sided, not a pill with the label taken out. A stadium-shaped button holding one
 * icon reads as a button whose text failed to load; the round one reads as an icon button, which is
 * what it has become. The two forms are two components, so the collapsed one inherits M3's icon
 * button metrics rather than restating them.
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
        // `transitionSpec` is not a composable scope, so everything it needs is resolved here and
        // captured. The size morph is spatial — it is a shape moving — and an overshooting size is
        // safe where an overshooting padding is not: Compose constrains it before laying out.
        val enter = Transitions.fade
        val exit = Transitions.fadeAway
        val sizeSpec = StxTheme.motion.spatial<IntSize>()
        AnimatedContent(
            targetState = expanded,
            transitionSpec = { enter togetherWith exit using SizeTransform { _, _ -> sizeSpec } },
            label = "responsiveButton",
        ) { wide ->
            if (wide) {
                ButtonSurface(
                    onClick = onClick,
                    variant = variant,
                    color = color,
                    enabled = enabled,
                    style = style,
                ) {
                    Icon(icon = icon, description = null)
                    Typography(text = text, variant = TypographyVariant.LabelLarge, maxLines = 1)
                }
            } else {
                IconButton(
                    icon = icon,
                    description = text,
                    onClick = onClick,
                    variant = variant,
                    color = color,
                    enabled = enabled,
                    style = style,
                )
            }
        }
    }
}
