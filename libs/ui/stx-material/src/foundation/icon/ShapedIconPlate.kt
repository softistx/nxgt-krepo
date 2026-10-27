package com.softistx.material.foundation.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * An [icon] centred on a plate of any [shape] (typically a `MaterialShapes.*.toShape()`): the one
 * decorative badge that a tone, an empty pane or any other marker is drawn with.
 *
 * Decorative: the icon carries no description, because the text beside it says what it means.
 */
@Composable
internal fun ShapedIconPlate(
    icon: ImageVector,
    shape: Shape,
    container: Color,
    ink: Color,
    size: Dp,
    iconSize: IconSize,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description = null, size = iconSize, tint = ink)
    }
}
