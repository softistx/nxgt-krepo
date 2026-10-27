package com.softistx.material.components.content

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * A chip that *does* something. Material 3's `AssistChip`.
 *
 * [Chip] is a filter — it stays selected. This one is a verb: "Call", "Open in maps", "Add to
 * calendar". There is no selected state, because the action already happened.
 */
@Composable
fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    style: Style = Style,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        label = { Typography(text = text, variant = TypographyVariant.LabelMedium) },
        modifier = modifier.styledBy(style, enabled = enabled, base = chipStyle, interactionSource = interactionSource),
        enabled = enabled,
        leadingIcon = leading,
        trailingIcon = trailing,
        interactionSource = interactionSource,
    )
}
