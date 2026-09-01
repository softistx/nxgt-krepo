package com.softistx.material.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.motion.shimmer
import com.softistx.material.theme.StxTheme

/**
 * The shape a piece of content will occupy, while it is still loading.
 *
 * ```kotlin
 * Skeleton()
 * ```
 *
 * The shimmer is on by default and stops on its own when motion is disabled, so a caller writes no
 * animation and a screenshot test needs no special case. A skeleton that merely sits there in grey
 * reads as a broken layout; the sweep is what makes it read as work in progress.
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .shimmer(),
    )
}
