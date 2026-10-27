package com.softistx.material.components.surface

import androidx.compose.foundation.style.Style
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.text.Typography

/** One row of a [Menu]. */
@Immutable
data class MenuItem(
    val label: String,
    val onClick: () -> Unit,
    val leading: ImageVector? = null,
    val enabled: Boolean = true,
)

/**
 * An anchored menu. Material 3's `DropdownMenu`.
 *
 * Place it next to the control that opens it; the popup positions itself. [items] is the common
 * case — a custom body still has `DropdownMenu` underneath if a caller needs a divider.
 *
 * [style] has no default look: M3's popup paints its own container over a background, so a
 * `background` or a hover layer here would be hidden. It is for what M3 cannot express (a size,
 * an offset, an alpha).
 */
@Composable
fun Menu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    style: Style = Style,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.styledBy(style),
        offset = offset,
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = { Typography(text = item.label) },
                onClick = {
                    onDismiss()
                    item.onClick()
                },
                leadingIcon =
                    item.leading?.let { icon ->
                        { Icon(icon = icon, description = null) }
                    },
                enabled = item.enabled,
            )
        }
    }
}
