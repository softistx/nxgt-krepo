package com.strange.material.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import com.strange.material.display.EmptyState
import com.strange.material.display.Skeleton

/**
 * An image that never flashes empty. Coil's `SubcomposeAsyncImage` with this library's
 * [Skeleton] while it loads and [EmptyState] if it fails.
 *
 * The application owns the `ImageLoader` (network, cache). This just paints.
 */
@Composable
fun StrangeImage(
    model: Any?,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = description,
        modifier = modifier,
        contentScale = contentScale,
        loading = { Skeleton(modifier = Modifier.fillMaxSize()) },
        error = {
            Box(Modifier.fillMaxSize()) {
                EmptyState(title = "Couldn't load the image")
            }
        },
    )
}
