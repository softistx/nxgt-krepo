package com.softistx.material.surface

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.softistx.material.icon.Icon
import com.softistx.material.text.Typography

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
 */
@Composable
fun Menu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
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
