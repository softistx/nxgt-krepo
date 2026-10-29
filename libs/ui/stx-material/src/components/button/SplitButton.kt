package com.softistx.material.components.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.Style
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.surface.Menu
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography
import com.softistx.material.foundation.theme.LocalStxStrings
import kotlinx.coroutines.launch

/**
 * A primary action with a sibling that opens alternatives. Material 3's `SplitButtonLayout`.
 *
 * The leading half is [text]; the trailing half is a chevron that opens [overflow]. Two actions
 * that look connected because they are — a Save plus a Save as, not two buttons in a row.
 *
 * Both halves morph through M3's own split-button shapes: each squares its inner corners while
 * pressed. The trailing half is a toggle that is checked while the menu is open, so it turns
 * round and its chevron points up. No press scale.
 *
 * [style] sees the whole control as one: its `pressed` and `hovered` blocks fire while either half
 * is pressed or hovered, and `disabled` while [enabled] is false. Each half keeps its own
 * interaction source for M3's shape morph, and both are forwarded into the one the style reads.
 */
@Composable
fun SplitButton(
    text: String,
    onClick: () -> Unit,
    overflow: List<MenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
) {
    var open by remember { mutableStateOf(false) }
    val leadingSource = remember { MutableInteractionSource() }
    val trailingSource = remember { MutableInteractionSource() }
    val styleSource = remember { MutableInteractionSource() }
    LaunchedEffect(leadingSource, trailingSource, styleSource) {
        launch { leadingSource.interactions.collect { styleSource.emit(it) } }
        launch { trailingSource.interactions.collect { styleSource.emit(it) } }
    }
    Box(modifier.styledBy(style, enabled = enabled, base = buttonStyle, interactionSource = styleSource)) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onClick,
                    enabled = enabled,
                    interactionSource = leadingSource,
                ) {
                    Typography(text = text)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = open,
                    onCheckedChange = { open = it },
                    enabled = enabled && overflow.isNotEmpty(),
                    interactionSource = trailingSource,
                ) {
                    Icon(icon = if (open) StxIcons.ChevronUp else StxIcons.ChevronDown, description = LocalStxStrings.current.moreActions)
                }
            },
        )
        Menu(expanded = open, onDismiss = { open = false }, items = overflow)
    }
}
