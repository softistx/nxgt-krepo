package com.strange.material.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.strange.material.theme.StrangeTheme

/** Adds [option] if it was off, removes it if it was on. The empty set is "everything". */
fun toggleFilter(
    selected: Set<String>,
    option: String,
): Set<String> = if (option in selected) selected - option else selected + option

/**
 * A row of filters as chips. Not the full filter subsystem: a set of names, a selected subset.
 *
 * Tapping a selected chip removes it; tapping another adds it. The empty set is "everything",
 * which is the only default that does not lie.
 */
@Composable
fun FilterBar(
    options: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.xs),
    ) {
        options.forEach { option ->
            val on = option in selected
            Chip(
                text = option,
                selected = on,
                onClick = { onChange(toggleFilter(selected, option)) },
            )
        }
    }
}
