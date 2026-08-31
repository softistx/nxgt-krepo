package com.strange.material.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.strange.material.button.IconButton
import com.strange.material.form.SelectField
import com.strange.material.icon.StrangeIcons
import com.strange.material.theme.StrangeTheme

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
 * is the usual next value.
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
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        SelectField(
            value = selected,
            onValueChange = onSelectedChange,
            options = options,
            modifier = Modifier.weight(1f),
            label = label,
            enabled = enabled,
        )
        IconButton(
            icon = if (direction == SortDirection.Asc) StrangeIcons.ChevronUp else StrangeIcons.ChevronDown,
            description = if (direction == SortDirection.Asc) "Ascending" else "Descending",
            onClick = { onDirectionChange(cycleSortDirection(direction)) },
            enabled = enabled,
        )
    }
}
