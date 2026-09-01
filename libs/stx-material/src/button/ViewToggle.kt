package com.softistx.material.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.icon.StrangeIcons
import com.softistx.material.theme.StrangeTheme

enum class ViewMode {
    List,
    Grid,
}

/**
 * List or grid. Two [IconToggle]s that exclude each other.
 *
 * Material 3 has no view switcher. A [com.softistx.material.navigation.SegmentedControl] would
 * also work, but an icon pair is the size this choice is: it sits in a toolbar, not in a form.
 */
@Composable
fun ViewToggle(
    value: ViewMode,
    onChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xxs),
    ) {
        IconToggle(
            icon = StrangeIcons.ViewList,
            description = "List",
            checked = value == ViewMode.List,
            onCheckedChange = { if (it) onChange(ViewMode.List) },
            enabled = enabled,
        )
        IconToggle(
            icon = StrangeIcons.ViewGrid,
            description = "Grid",
            checked = value == ViewMode.Grid,
            onCheckedChange = { if (it) onChange(ViewMode.Grid) },
            enabled = enabled,
        )
    }
}
