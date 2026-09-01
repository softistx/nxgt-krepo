package com.softistx.material.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.softistx.material.theme.StxTheme

/** One row of a [Checklist]. */
@Immutable
data class CheckItem(
    val label: String,
    val checked: Boolean = false,
)

/** Flips the item at [index], leaving the others as they were. */
fun toggleCheckItem(
    items: List<CheckItem>,
    index: Int,
): List<CheckItem> =
    items.mapIndexed { i, item ->
        if (i == index) item.copy(checked = !item.checked) else item
    }

/**
 * A list of boxes. Each row is a [Checkbox], so the label is part of the target.
 *
 * The list holds [CheckItem]s rather than a parallel `List<Boolean>`, so reordering the labels
 * cannot silently change what is ticked — the same rule as [CheckboxGroup].
 */
@Composable
fun Checklist(
    items: List<CheckItem>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.xxs),
    ) {
        items.forEachIndexed { index, item ->
            Checkbox(
                value = item.checked,
                onValueChange = { onToggle(index) },
                label = item.label,
                enabled = enabled,
            )
        }
    }
}
