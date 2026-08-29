package com.strange.material.icon

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon as MaterialIcon

/**
 * An icon at a named size, with the accessibility question asked rather than assumed.
 *
 * [description] is required and nullable — nullable because a genuinely decorative icon next to
 * its own label must *not* be announced twice, required because the alternative is a parameter
 * with a default that nobody ever revisits. Making the caller type `description = null` is the
 * cheapest way to make the choice deliberate.
 */
@Composable
fun Icon(
    icon: ImageVector,
    description: String?,
    modifier: Modifier = Modifier,
    size: IconSize = IconSize.Medium,
    tint: Color = LocalContentColor.current,
) {
    MaterialIcon(
        imageVector = icon,
        contentDescription = description,
        modifier = modifier.size(size.dp),
        tint = tint,
    )
}

/** The icon sizes this library draws at, so a row of icons never disagrees by two pixels. */
enum class IconSize(
    val dp: Dp,
) {
    Small(16.dp),
    Medium(20.dp),
    Large(24.dp),
    XLarge(32.dp),
}
