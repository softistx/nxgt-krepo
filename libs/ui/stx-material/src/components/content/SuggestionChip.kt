package com.softistx.material.components.content

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant
import androidx.compose.material3.SuggestionChip as MaterialSuggestionChip

/**
 * A chip that offers a completion. Material 3's `SuggestionChip`.
 *
 * Search history, "try this instead" — it is neither a filter ([Chip]) nor a verb ([ActionChip]).
 * Tapping one usually fills a field; the caller decides.
 */
@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
    leading: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    MaterialSuggestionChip(
        onClick = onClick,
        label = { Typography(text = text, variant = TypographyVariant.LabelMedium) },
        modifier = modifier.styledBy(style, enabled = enabled, base = chipStyle, interactionSource = interactionSource),
        enabled = enabled,
        icon = leading,
        interactionSource = interactionSource,
    )
}
