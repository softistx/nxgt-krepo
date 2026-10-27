package com.softistx.material.components.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.style.pressable
import com.softistx.material.foundation.theme.StxTheme

/**
 * A grid of images. Columns follow [GridCells.Adaptive], so a wide pane shows more without a
 * `when` on width. With [onSelect] a tile is a button that presses to 0.99; without it [style] has
 * nothing to dress.
 */
@Composable
fun Gallery(
    images: List<Any>,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    minSize: Dp = 160.dp,
    style: Style = Style,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm),
    ) {
        itemsIndexed(images) { index, model ->
            StxImage(
                model = model,
                description = null,
                modifier =
                    Modifier
                        .aspectRatio(1f)
                        .pressable(tileStyle, style, onSelect?.let { select -> { select(index) } }),
            )
        }
    }
}
