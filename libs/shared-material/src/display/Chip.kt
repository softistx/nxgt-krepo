package com.strange.material.display

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.strange.material.text.Typography
import com.strange.material.text.TypographyVariant

/**
 * A chip, selectable. Material 3's `FilterChip`, which already animates its own selected state,
 * carries the tick-mark affordance and morphs its shape on press.
 *
 * A chip with no [onClick] is still a `FilterChip` — it simply does nothing when tapped. Material 3
 * has no non-interactive chip, and building one from a `Row` to avoid a ripple would cost the
 * shape, the border, the selected semantics and the disabled treatment that make the interactive
 * one right. If a tag is genuinely inert, [StatusBadge] is the component that says so.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    style: Style = Style,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // A chip with no `onClick` does nothing when tapped, so lighting it up under the pointer would
    // promise an interaction that is not there.
    val hovered by interactionSource.collectIsHoveredAsState()
    val styleState =
        rememberUpdatedStyleState(interactionSource) {
            it.isEnabled = enabled
            it.isSelected = selected
        }
    FilterChip(
        selected = selected,
        onClick = onClick ?: {},
        label = { Typography(text = text, variant = TypographyVariant.LabelMedium) },
        modifier = modifier.styleable(styleState, chipStyle, style),
        enabled = enabled,
        leadingIcon = leading,
        trailingIcon = trailing,
        colors = chipColors(hovered = hovered && enabled && onClick != null),
        interactionSource = interactionSource,
    )
}
