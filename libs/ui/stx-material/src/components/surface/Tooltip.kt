package com.softistx.material.components.surface

import androidx.compose.foundation.style.Style
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * A short label on hover or long-press. Material 3's `TooltipBox` + `PlainTooltip`.
 *
 * Desktop gets it on hover, touch on long-press — M3 already splits that. The caller wraps the
 * anchor and never remembers a `TooltipState`.
 * [style] dresses the anchor and has no default look: M3 paints the label.
 */
@Composable
fun Tooltip(
    text: String,
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState()
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip { Typography(text = text, variant = TypographyVariant.BodySmall) }
        },
        state = state,
        modifier = modifier.styledBy(style),
        content = content,
    )
}
