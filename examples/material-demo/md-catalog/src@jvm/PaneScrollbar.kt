package com.strange.material.demo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop has a pointer and no overscroll, so it gets the bar. */
@Composable
actual fun PaneScrollbar(
    state: LazyListState,
    modifier: Modifier,
) {
    VerticalScrollbar(adapter = rememberScrollbarAdapter(state), modifier = modifier)
}

@Composable
actual fun PaneScrollbar(
    state: ScrollState,
    modifier: Modifier,
) {
    VerticalScrollbar(adapter = rememberScrollbarAdapter(state), modifier = modifier)
}
