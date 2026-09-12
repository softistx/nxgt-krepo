package com.softistx.material.display

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.softistx.material.text.Typography
import com.softistx.material.text.TypographyVariant
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
    val styleState = rememberUpdatedStyleState(interactionSource) { it.isEnabled = enabled }
    MaterialSuggestionChip(
        onClick = onClick,
        label = { Typography(text = text, variant = TypographyVariant.LabelMedium) },
        modifier = modifier.styleable(styleState, chipStyle, style),
        enabled = enabled,
        icon = leading,
        interactionSource = interactionSource,
    )
}
