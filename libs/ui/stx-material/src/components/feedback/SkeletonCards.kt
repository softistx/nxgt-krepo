package com.softistx.material.components.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.softistx.material.components.content.Card
import com.softistx.material.foundation.motion.animateStagger
import com.softistx.material.foundation.theme.StxTheme

/**
 * [count] card-shaped placeholders that shimmer while the data is on its way, arriving one after the
 * other. With reduced motion they appear at once and do not shimmer.
 */
@Composable
fun SkeletonCards(
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(StxTheme.spacing.sm)) {
        repeat(count) { index ->
            Card(modifier = Modifier.fillMaxWidth().animateStagger(index)) {
                Skeleton(height = 18.dp, modifier = Modifier.fillMaxWidth(0.5f))
                Skeleton()
                Skeleton(modifier = Modifier.fillMaxWidth(0.8f))
            }
        }
    }
}
