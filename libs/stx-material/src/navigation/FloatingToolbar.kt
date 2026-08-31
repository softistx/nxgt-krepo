package com.strange.material.navigation

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A toolbar that sits on the content rather than in a bar.
 *
 * Material 3's `HorizontalFloatingToolbar` — the modern replacement for a row of FABs. [expanded]
 * is the caller's to drive (a scroll offset, a selection); the toolbar does the rest, including
 * the collapse that leaves [leading] and [trailing] visible. The actions inside are the interactive
 * things, so they take `style`, not this container.
 */
@Composable
fun FloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    leading: @Composable (RowScope.() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = expanded,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        leadingContent = leading,
        trailingContent = trailing,
        content = content,
    )
}
