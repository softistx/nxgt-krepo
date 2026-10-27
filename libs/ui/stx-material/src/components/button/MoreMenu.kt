package com.softistx.material.components.button

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.softistx.material.components.surface.Menu
import com.softistx.material.components.surface.MenuItem
import com.softistx.material.foundation.icon.IconSize
import com.softistx.material.foundation.icon.StxIcons

/**
 * The trailing "more" on a row. An [IconButton] that opens a [Menu].
 *
 * Every list item that grew a ghost button and a `DropdownMenu` wrote this. The items are the
 * same [MenuItem] a standalone menu takes, so a row and a toolbar can share a list.
 */
@Composable
fun MoreMenu(
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    description: String = "More",
    variant: ButtonVariant = ButtonVariant.Ghost,
    color: ButtonColor = ButtonColor.Neutral,
    size: IconSize = IconSize.Medium,
    enabled: Boolean = true,
    style: Style = Style,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            icon = StxIcons.MoreHoriz,
            description = description,
            onClick = { open = true },
            variant = variant,
            color = color,
            size = size,
            enabled = enabled && items.isNotEmpty(),
            style = style,
        )
        Menu(expanded = open, onDismiss = { open = false }, items = items)
    }
}
