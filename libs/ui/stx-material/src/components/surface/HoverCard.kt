package com.softistx.material.components.surface

import androidx.compose.foundation.style.Style
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.Button
import com.softistx.material.components.button.ButtonVariant
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.text.TypographyVariant

/**
 * A richer hover (or long-press) card: title, body, optional action.
 *
 * Material 3's `RichTooltip`, persistent so the action can be reached. A [Tooltip] is a short
 * label; this is a preview of the thing under the pointer.
 * [style] dresses the anchor and has no default look: M3 paints the card.
 */
@Composable
fun HoverCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(
                title = { Typography(text = title, variant = TypographyVariant.TitleSmall) },
                action =
                    if (action != null && onAction != null) {
                        { Button(text = action, onClick = onAction, variant = ButtonVariant.Link) }
                    } else {
                        null
                    },
            ) {
                Typography(text = text)
            }
        },
        state = state,
        modifier = modifier.styledBy(style),
        content = content,
    )
}
