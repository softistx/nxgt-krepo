package com.strange.material.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strange.material.theme.StrangeTheme

/**
 * A grid of images. Columns follow [GridCells.Adaptive], so a wide pane shows more without a
 * `when` on width.
 */
@Composable
fun Gallery(
    images: List<Any>,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    minSize: Dp = 160.dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(StrangeTheme.spacing.sm),
    ) {
        itemsIndexed(images) { index, model ->
            StrangeImage(
                model = model,
                description = null,
                modifier =
                    Modifier
                        .aspectRatio(1f)
                        .then(
                            if (onSelect != null) {
                                Modifier.clickable { onSelect(index) }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}
