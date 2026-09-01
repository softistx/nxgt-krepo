package com.softistx.material.demo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The bar down the edge of a pane that can be scrolled.
 *
 * A desktop pane with no scrollbar reads as a pane with nothing below the fold: the wheel works,
 * but nothing says it does, and whatever was added last is simply not found. On a touch screen the
 * opposite is true — a permanent bar is clutter and the content's own overscroll says everything —
 * so this is `expect`/`actual` rather than a flag, and the Android side draws nothing.
 */
@Composable
expect fun PaneScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
)

/** The same, for a pane scrolled with `Modifier.verticalScroll`. */
@Composable
expect fun PaneScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
)
