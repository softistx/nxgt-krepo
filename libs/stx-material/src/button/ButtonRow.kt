package com.softistx.material.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.theme.StrangeTheme

/**
 * A row of actions — the bottom of a dialog, the end of a form.
 *
 * This is **not** Material 3's `ButtonGroup`, and it is named apart from it deliberately: M3's is a
 * connected, segmented control whose buttons share edges and morph on press, and it takes a
 * `ButtonGroupScope` rather than a `RowScope`. That component is the right one for a segmented
 * switcher, and phase 3 will reach for it by its own name. This one is a layout: separate buttons,
 * spaced by a token, aligned to one end.
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.End,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm, align),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
