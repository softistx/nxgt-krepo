package com.softistx.material.components.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softistx.material.foundation.style.styledBy
import com.softistx.material.foundation.theme.StxTheme

/**
 * A snapping row of pages: one large page and the next one peeking by [peek], in M3's multi-browse carousel.
 *
 * The large page takes the width left by [peek] and the gap; the peeking page is [peek] wide and the pages
 * in between shrink into it, each page clipped to a mask that morphs with its size, so the content of
 * [page] is cut to a rounded shape whatever its size. Dots below track the settled page.
 *
 * [style] dresses the carousel as a whole (the track and its dots), never the pages. It is a layout wrapper
 * with no default look and no input behind it, so only static properties and `disabled` apply.
 */
@Composable
fun Carousel(
    count: Int,
    modifier: Modifier = Modifier,
    peek: Dp = 48.dp,
    style: Style = Style,
    page: @Composable (index: Int) -> Unit,
) {
    val state = rememberCarouselState { count }
    val spacing = StxTheme.spacing.sm
    val shape = MaterialTheme.shapes.extraLarge
    Column(modifier = modifier.styledBy(style), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            HorizontalMultiBrowseCarousel(
                state = state,
                preferredItemWidth = (maxWidth - peek - spacing).coerceAtLeast(peek),
                itemSpacing = spacing,
                minSmallItemWidth = peek,
                maxSmallItemWidth = peek,
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                Box(Modifier.maskClip(shape)) { page(index) }
            }
        }
        Dots(count = count, selected = state.currentItem)
    }
}

@Composable
private fun Dots(
    count: Int,
    selected: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val active = index == selected
            Box(
                Modifier
                    .padding(StxTheme.spacing.xxs)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}
