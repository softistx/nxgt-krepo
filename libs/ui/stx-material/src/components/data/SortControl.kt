package com.softistx.material.components.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.softistx.material.components.button.IconButton
import com.softistx.material.components.form.SelectField
import com.softistx.material.foundation.icon.StxIcons
import com.softistx.material.foundation.theme.StxTheme

enum class SortDirection {
    Asc,
    Desc,
}

fun cycleSortDirection(direction: SortDirection): SortDirection =
    if (direction == SortDirection.Asc) SortDirection.Desc else SortDirection.Asc

/**
 * Which column, and which way. A [SelectField] plus a direction control.
 *
 * Material 3 has no sort chrome. The field names the key; the icon names the direction, so a
 * screen reader hears "Customer, descending" rather than two unlabelled buttons. [cycleSortDirection]
 * is the usual next value. [style] goes to both; the button already carries
 * `StxStyles.button`, the field (M3's) has no default.
 */
@Composable
fun SortControl(
    options: List<String>,
    selected: String?,
    direction: SortDirection,
    onSelectedChange: (String) -> Unit,
    onDirectionChange: (SortDirection) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Sort by",
    enabled: Boolean = true,
    style: Style = Style,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.xs),
    ) {
        SelectField(
            value = selected,
            onValueChange = onSelectedChange,
            options = options,
            modifier = Modifier.weight(1f),
            label = label,
            enabled = enabled,
            style = style,
        )
        IconButton(
            icon = if (direction == SortDirection.Asc) StxIcons.ChevronUp else StxIcons.ChevronDown,
            description = if (direction == SortDirection.Asc) "Ascending" else "Descending",
            onClick = { onDirectionChange(cycleSortDirection(direction)) },
            enabled = enabled,
            style = style,
        )
    }
}
