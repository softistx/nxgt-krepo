package com.strange.material.button

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.strange.material.icon.Icon
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

/**
 * The one action that floats over the content. Material 3's FAB.
 *
 * Pass [text] and it becomes the extended FAB — icon plus label — collapsing to the icon when
 * [expanded] is false. Without [text] it is the round button, which is the common case.
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
) {
    val tone = buttonTone(color)
    val leading = @Composable { Icon(icon = icon, description = description) }
    if (text == null) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = tone.container,
            contentColor = tone.onContainer,
            content = leading,
        )
    } else {
        ExtendedFloatingActionButton(
            text = { Typography(text = text, variant = TypographyVariant.LabelLarge) },
            icon = leading,
            onClick = onClick,
            modifier = modifier,
            expanded = expanded,
            containerColor = tone.container,
            contentColor = tone.onContainer,
        )
    }
}
