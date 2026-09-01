package com.softistx.material.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.theme.StxTheme

/**
 * A grid whose column count follows the width it is offered, not the window.
 *
 * Foundation's `LazyVerticalGrid` with [GridCells.Adaptive]. A card that needs 200 dp stays
 * readable on a phone and tiles on a desktop without a `when` on breakpoints.
 */
@Composable
fun <T> ResponsiveGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    minSize: Dp = 200.dp,
    item: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
    ) {
        items(items) { value -> item(value) }
    }
}
