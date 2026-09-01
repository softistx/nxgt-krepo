package com.softistx.material.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.theme.StrangeTheme

/**
 * A snapping row of pages, with the next card peeking.
 *
 * Foundation's `HorizontalPager`. The peek is padding on the end, so the following page is visible
 * without a custom `PageSize`. Dots below track the settled page.
 */
@Composable
fun Carousel(
    count: Int,
    modifier: Modifier = Modifier,
    peek: Dp = 48.dp,
    page: @Composable (index: Int) -> Unit,
) {
    val state = rememberPagerState { count }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(end = peek),
            pageSpacing = StrangeTheme.spacing.sm,
            modifier = Modifier.fillMaxWidth(),
        ) { index ->
            page(index)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(count) { index ->
                val selected = index == state.currentPage
                Box(
                    Modifier
                        .padding(StrangeTheme.spacing.xxs)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
    }
}
