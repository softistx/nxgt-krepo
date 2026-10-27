package com.softistx.material.components.button

import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.softistx.material.foundation.icon.Icon
import com.softistx.material.foundation.icon.StxIcons

enum class ViewMode {
    List,
    Grid,
}

/**
 * List or grid. Two icon toggles joined as Material 3's connected button group: the chosen one is
 * filled, the pair shares one rounded outline, and each announces `Role.RadioButton`.
 *
 * Material 3 has no view switcher. A [com.softistx.material.components.navigation.SegmentedControl] would
 * also work, but an icon pair is the size this choice is: it sits in a toolbar, not in a form.
 */
@Composable
fun ViewToggle(
    value: ViewMode,
    onChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: Style = Style,
) {
    ConnectedToggleGroup(
        count = ViewMode.entries.size,
        selected = value.ordinal,
        onSelect = { onChange(ViewMode.entries[it]) },
        modifier = modifier,
        enabled = enabled,
        style = style,
    ) { index ->
        val mode = ViewMode.entries[index]
        Icon(
            icon = if (mode == ViewMode.List) StxIcons.ViewList else StxIcons.ViewGrid,
            description = mode.name,
        )
    }
}
