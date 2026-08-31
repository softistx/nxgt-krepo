package com.strange.material.display

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

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
    style: Style = Style,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    AssistChip(
        onClick = onClick,
        label = { Typography(text = text, variant = TypographyVariant.LabelMedium) },
        modifier = modifier.styleable(styleState, chipStyle, style),
        enabled = enabled,
        leadingIcon = leading,
        trailingIcon = trailing,
        interactionSource = interactionSource,
    )
}
